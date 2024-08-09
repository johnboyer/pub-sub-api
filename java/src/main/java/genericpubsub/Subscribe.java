package genericpubsub;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.FetchRequest;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import com.salesforce.eventbus.protobuf.SchemaRequest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import com.google.protobuf.ByteString;

import io.grpc.stub.StreamObserver;
import utility.CommonContext;
import utility.ExampleConfigurations;

/**
 * A single-topic subscriber that consumes events using Event Bus API Subscribe RPC. The example demonstrates how to:
 * - implement a long-lived subscription to a single topic
 * - a basic flow control strategy
 * - a basic retry strategy.
 *
 * Example:
 * ./run.sh genericpubsub.Subscribe
 *
 * @author sidd0610
 */
public class Subscribe extends CommonContext implements ObserverContext {

    public static int BATCH_SIZE;
    public static int MAX_RETRIES = 3;
    public static String ERROR_REPLAY_ID_VALIDATION_FAILED = "fetch.replayid.validation.failed";
    public static String ERROR_REPLAY_ID_INVALID = "fetch.replayid.corrupted";
    public static String ERROR_SERVICE_UNAVAILABLE = "service.unavailable";
    public static int SERVICE_UNAVAILABLE_WAIT_BEFORE_RETRY_SECONDS = 5;
    public static ExampleConfigurations exampleConfigurations;
    public static AtomicBoolean isActive = new AtomicBoolean(false);
    public static AtomicInteger retriesLeft = new AtomicInteger(MAX_RETRIES);
    private StreamObserver<FetchRequest> serverStream;
    private Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
    private final StreamObserver<FetchResponse> responseStreamObserver;
    private final ReplayPreset replayPreset;
    private final ByteString customReplayId;
    private final ScheduledExecutorService retryScheduler;

    private final boolean processChangedFields;

    public Subscribe(ExampleConfigurations exampleConfigurations) {
        super(exampleConfigurations);
        isActive.set(true);
        this.exampleConfigurations = exampleConfigurations;
        this.BATCH_SIZE = exampleConfigurations.getNumberOfEventsToSubscribeInEachFetchRequest();
        this.responseStreamObserver = getDefaultResponseStreamObserver();
        this.setupTopicDetails(exampleConfigurations.getTopic(), false, false);
        this.replayPreset = exampleConfigurations.getReplayPreset();
        this.customReplayId = exampleConfigurations.getReplayId();
        this.retryScheduler = Executors.newScheduledThreadPool(1);
        this.processChangedFields = exampleConfigurations.getProcessChangedFields();
    }

    public Subscribe(ExampleConfigurations exampleConfigurations, StreamObserver<FetchResponse> responseStreamObserver) {
        super(exampleConfigurations);
        isActive.set(true);
        this.exampleConfigurations = exampleConfigurations;
        this.BATCH_SIZE = exampleConfigurations.getNumberOfEventsToSubscribeInEachFetchRequest();
        this.responseStreamObserver = responseStreamObserver;
        this.setupTopicDetails(exampleConfigurations.getTopic(), false, false);
        this.replayPreset = exampleConfigurations.getReplayPreset();
        this.customReplayId = exampleConfigurations.getReplayId();
        this.retryScheduler = Executors.newScheduledThreadPool(1);
        this.processChangedFields = exampleConfigurations.getProcessChangedFields();
    }

    /**
     * Function to start the subscription.
     */
    public void startSubscription() {
        logger.info("Subscription started for topic: " + busTopicName + ".");
        fetch(BATCH_SIZE, busTopicName, replayPreset, customReplayId);
        // Thread being blocked here for demonstration of this specific example. Blocking the thread in production is not recommended.
        while(isActive.get()) {
            waitInMillis(5_000);
            logger.info("Subscription Active. Received a total of " + receivedEvents.get() + " events.");
        }
    }

    /** Helper function to send FetchRequests.
     * @param providedBatchSize
     * @param providedTopicName
     * @param providedReplayPreset
     * @param providedReplayId
     */
    public void fetch(int providedBatchSize, String providedTopicName, ReplayPreset providedReplayPreset, ByteString providedReplayId) {
        serverStream = asyncStub.subscribe(this.responseStreamObserver);
        FetchRequest.Builder fetchRequestBuilder = FetchRequest.newBuilder()
                .setNumRequested(providedBatchSize)
                .setTopicName(providedTopicName)
                .setReplayPreset(providedReplayPreset);
        if (providedReplayPreset == ReplayPreset.CUSTOM) {
            logger.info("Subscription has Replay Preset set to CUSTOM. In this case, the events will be delivered from provided ReplayId.");
            fetchRequestBuilder.setReplayId(providedReplayId);
        }
        serverStream.onNext(fetchRequestBuilder.build());
    }

    /**
     * Function to decide the delay (in ms) in sending FetchRequests using
     * Binary Exponential Backoff - Waits for 2^(Max Number of Retries - Retries Left) * 1000.
     */
    public long getBackoffWaitTime() {
        long waitTime = (long) (Math.pow(2, MAX_RETRIES - retriesLeft.get()) * 1000);
        return waitTime;
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

    /**
     * Creates a StreamObserver for handling the incoming FetchResponse messages from the server.
     *
     * @return
     */
    private StreamObserver<FetchResponse> getDefaultResponseStreamObserver() {
        return new FetchResponseStreamObserver(this);
    }

    /**
     * A Runnable class that is used to send the FetchRequests by making a new Subscribe call while retrying on
     * receiving an error. This is done in order to avoid blocking the thread while waiting for retries. This class is
     * passed to the ScheduledExecutorService which will asynchronously send the FetchRequests during retries.
     */
    private class RetryRequestSender implements Runnable {
        private ReplayPreset retryReplayPreset;
        private ByteString retryReplayId;
        public RetryRequestSender(ReplayPreset replayPreset, ByteString replayId) {
            this.retryReplayPreset = replayPreset;
            this.retryReplayId = replayId;
        }

        @Override
        public void run() {
            fetch(BATCH_SIZE, busTopicName, retryReplayPreset, retryReplayId);
            logger.info("Retry FetchRequest Sent.");
        }
    }

    /**
     * Helper function to process the events received.
     */
    @Override
    public void processEvent(ConsumerEvent ce) throws IOException {
        Schema writerSchema = getSchema(ce.getEvent().getSchemaId());
        GenericRecord record = deserialize(writerSchema, ce.getEvent().getPayload());
        logger.info("Received event with payload: " + record.toString() + " with schema name: " + writerSchema.getName());
        if (processChangedFields) {
            // This example expands the changedFields bitmap field in ChangeEventHeader.
            // To expand the other bitmap fields, i.e., diffFields and nulledFields, replicate or modify this code.
            processAndPrintBitmapFields(writerSchema, record, "changedFields");
        }
    }

    @Override
    public void deactivate() {
        isActive.set(false);
    }

    @Override
    public void fetchMore() {
        fetchMore(BATCH_SIZE);
    }

    /**
     * Helper function to get the schema of an event if it does not already exist in the schema cache.
     */
    public Schema getSchema(String schemaId) {
        return schemaCache.computeIfAbsent(schemaId, id -> {
            SchemaRequest request = SchemaRequest.newBuilder().setSchemaId(id).build();
            String schemaJson = blockingStub.getSchema(request).getSchemaJson();
            return (new Schema.Parser()).parse(schemaJson);
        });
    }

    /**
     * Helps keep the subscription active by sending FetchRequests at regular intervals.
     *
     * @param numEvents
     */
    public void fetchMore(int numEvents) {
        FetchRequest fetchRequest = FetchRequest.newBuilder().setTopicName(this.busTopicName)
                .setNumRequested(numEvents).build();
        serverStream.onNext(fetchRequest);
    }


    public int getBatchSize() {
        return BATCH_SIZE;
    }

    /**
     * Closes the connection when the task is complete.
     */
    @Override
    public synchronized void close() {
        try {
            if (serverStream != null) {
                serverStream.onCompleted();
            }
            if (retryScheduler != null) {
                retryScheduler.shutdown();
            }
        } catch (Exception e) {
            logger.info(e.toString());
        }
        super.close();
    }

    public static void main(String args[]) throws IOException  {
        ExampleConfigurations exampleConfigurations = new ExampleConfigurations("arguments.yaml");

        // Using the try-with-resource statement. The CommonContext class implements AutoCloseable in
        // order to close the resources used.
        try (Subscribe subscribe = new Subscribe(exampleConfigurations)) {
            subscribe.startSubscription();
        } catch (Exception e) {
            printStatusRuntimeException("Error during Subscribe", e);
        }
    }

}
