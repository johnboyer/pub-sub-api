package genericpubsub;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.salesforce.eventbus.protobuf.CommitReplayRequest;
import com.salesforce.eventbus.protobuf.CommitReplayResponse;
import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.ManagedFetchRequest;
import com.salesforce.eventbus.protobuf.ManagedFetchResponse;
import com.salesforce.eventbus.protobuf.SchemaRequest;
import config.PubSubApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import utility.CommonContext;

/**
 * A single-topic subscriber that consumes events using Event Bus API ManagedSubscribe RPC. The example demonstrates how to:
 * - implement a long-lived subscription to a single topic
 * - a basic flow control strategy
 * - a basic commits strategy.
 * <p>
 * Example:
 * ./run.sh genericpubsub.ManagedSubscribe
 *
 * @author jalaya
 */
@Slf4j
public class ManagedSubscribe extends CommonContext implements StreamObserver<ManagedFetchResponse> {
    private static int BATCH_SIZE;
    private StreamObserver<ManagedFetchRequest> serverStream;
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
    private final CountDownLatch serverOnCompletedLatch = new CountDownLatch(1);
    public static AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicInteger receivedEvents = new AtomicInteger(0);
    private final String developerName;
    private final String managedSubscriptionId;
    private final boolean processChangedFields;

    public ManagedSubscribe(PubSubApiConfig pubSubApiConfig) {
        super(pubSubApiConfig);
        isActive.set(true);
        var pubsub = pubSubApiConfig.getPubsub();
        this.managedSubscriptionId = pubsub.getManagedSubscriptionId();
        this.developerName = pubsub.getManagedSubscriptionDeveloperName();
        BATCH_SIZE = pubsub.getSubscriptionRequestSize();
        this.processChangedFields = pubsub.getLogChangedEventHeaders();
    }

    /**
     * Function to start the ManagedSubscription, and send first ManagedFetchRequest.
     */
    public void startManagedSubscription() {
        serverStream = asyncStub.managedSubscribe(this);
        ManagedFetchRequest.Builder builder = ManagedFetchRequest.newBuilder().setNumRequested(BATCH_SIZE);

        if (Objects.nonNull(managedSubscriptionId)) {
            builder.setSubscriptionId(managedSubscriptionId);
            log.info("Starting managed subscription with ID {}", managedSubscriptionId);
        } else if (Objects.nonNull(developerName)) {
            builder.setDeveloperName(developerName);
            log.info("Starting managed subscription with developer name {}", developerName);
        } else {
            log.warn("No ID or developer name specified");
        }

        serverStream.onNext(builder.build());

        // Thread being blocked here for demonstration of this specific example. Blocking the thread in production is not recommended.
        while(isActive.get()) {
            waitInMillis(5_000);
            log.info("Subscription Active. Received a total of " + receivedEvents.get() + " events.");
        }
    }

    /**
     * Helps keep the subscription active by sending FetchRequests at regular intervals.
     *
     * @param numOfRequestedEvents
     */
    private void fetchMore(int numOfRequestedEvents) {
        log.info("Fetching more events: {}", numOfRequestedEvents);
        ManagedFetchRequest fetchRequest = ManagedFetchRequest
                .newBuilder()
                .setNumRequested(numOfRequestedEvents)
                .build();
        serverStream.onNext(fetchRequest);
    }

    /**
     * Helper function to process the events received.
     */
    private void processEvent(ManagedFetchResponse response) throws IOException {
        if (response.getEventsCount() > 0) {
            for (ConsumerEvent event : response.getEventsList()) {
                String schemaId = event.getEvent().getSchemaId();
                log.info("processEvent - EventID: {} SchemaId: {}", event.getEvent().getId(), schemaId);
                Schema writerSchema = getSchema(schemaId);
                GenericRecord record = deserialize(writerSchema, event.getEvent().getPayload());
                log.info("Received event: {}", record.toString());
                if (processChangedFields) {
                    // This example expands the changedFields bitmap field in ChangeEventHeader.
                    // To expand the other bitmap fields, i.e., diffFields and nulledFields, replicate or modify this code.
                    processAndPrintBitmapFields(writerSchema, record, "changedFields");
                }
            }
            log.info("Processed batch of {} event(s)", response.getEventsList().size());
        }

        // Commit the replay after processing batch of events or commit the latest replay on an empty batch
        if (!response.hasCommitResponse()) {
            doCommitReplay(response.getLatestReplayId());
        }
    }

    /**
     * Helper function to commit the latest replay received from the server.
     */
    private void doCommitReplay(ByteString commitReplayId) {
        String newKey = UUID.randomUUID().toString();
        ManagedFetchRequest.Builder fetchRequestBuilder = ManagedFetchRequest.newBuilder();
        CommitReplayRequest commitRequest = CommitReplayRequest.newBuilder()
                .setCommitRequestId(newKey)
                .setReplayId(commitReplayId)
                .build();
        fetchRequestBuilder.setCommitReplayIdRequest(commitRequest);

        log.info("Sending CommitRequest with CommitReplayRequest ID: {}" , newKey);
        serverStream.onNext(fetchRequestBuilder.build());
    }

    /**
     * Helper function to inspect the status of a commitRequest.
     */
    private void checkCommitResponse(ManagedFetchResponse fetchResponse) {
        CommitReplayResponse ce = fetchResponse.getCommitResponse();
        try {
            if (ce.hasError()) {
                log.info("Failed Commit CommitRequestID: {} with error: {} with process time: {}",
                        ce.getCommitRequestId(), ce.getError().getMsg(), ce.getProcessTime());
                return;
            }
            log.info("Successfully committed replay with CommitRequestId: {} with process time: {}",
                    ce.getCommitRequestId(), ce.getProcessTime());
        } catch (Exception e) {
            log.warn(e.getMessage());
            abort(new RuntimeException("Client received error. Closing Call." + e));
        }
    }

    @Override
    public void onNext(ManagedFetchResponse fetchResponse) {
        int batchSize = fetchResponse.getEventsList().size();
        log.info("ManagedFetchResponse batch of {} events pending requested: {}", batchSize, fetchResponse.getPendingNumRequested());
        log.info("RPC ID: {}", fetchResponse.getRpcId());

        if (fetchResponse.hasCommitResponse()) {
            checkCommitResponse(fetchResponse);
        }
        try {
            processEvent(fetchResponse);
        } catch (IOException e) {
            log.warn(e.getMessage());
            abort(new RuntimeException("Client received error. Closing Call." + e));
        }

        synchronized (this) {
            receivedEvents.addAndGet(batchSize);
            this.notifyAll();
            if (!isActive.get()) {
                return;
            }
        }

        if (fetchResponse.getPendingNumRequested() == 0) {
            fetchMore(BATCH_SIZE);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        printStatusRuntimeException("Error during subscribe stream", (Exception) throwable);

        // onError from server closes stream. notify waiting thread that subscription is no longer active.
        synchronized (this) {
            isActive.set(false);
            this.notifyAll();
        }
    }

    @Override
    public void onCompleted() {
        log.info("Call completed by Server");
        synchronized (this) {
            isActive.set(false);
            this.notifyAll();
        }
        serverOnCompletedLatch.countDown();
    }

    /**
     * Helper function to get the schema of an event if it does not already exist in the schema cache.
     */
    private Schema getSchema(String schemaId) {
        return schemaCache.computeIfAbsent(schemaId, id -> {
            SchemaRequest request = SchemaRequest.newBuilder().setSchemaId(id).build();
            String schemaJson = blockingStub.getSchema(request).getSchemaJson();
            return (new Schema.Parser()).parse(schemaJson);
        });
    }

    /**
     * Closes the connection when the task is complete.
     */
    @Override
    public synchronized void close() {
        if (Objects.nonNull(serverStream)) {
            try {
                if (isActive.get()) {
                    isActive.set(false);
                    this.notifyAll();
                    serverStream.onCompleted();
                }
                serverOnCompletedLatch.await(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("interrupted while waiting to close ", e);
            }
        }
        super.close();
    }

    /**
     * Helper function to terminate the client on errors.
     */
    private synchronized void abort(Exception e) {
        serverStream.onError(e);
        isActive.set(false);
        this.notifyAll();
    }

    /**
     * Helper function to halt the current thread.
     */
    public void waitInMillis(long duration) {
        synchronized (this) {
            try {
                this.wait(duration);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws IOException  {
        PubSubApiConfig pubSubApiConfig = PubSubApiConfig.getPubSubApiConfig();

        // Using the try-with-resource statement. The CommonContext class implements AutoCloseable in
        // order to close the resources used.
        try (ManagedSubscribe subscribe = new ManagedSubscribe(pubSubApiConfig)) {
            subscribe.startManagedSubscription();
        } catch (Exception e) {
            printStatusRuntimeException("Error during ManagedSubscribe", e);
        }
    }
}
