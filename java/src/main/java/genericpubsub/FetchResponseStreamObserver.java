package genericpubsub;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utility.CommonContext;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FetchResponseStreamObserver implements StreamObserver<FetchResponse> {
    protected static final Logger logger = LoggerFactory.getLogger(FetchResponseStreamObserver.class);
    private final ObserverContext subscribe;

    private final AtomicInteger receivedEvents = new AtomicInteger(0);
    // Replay should be stored in replay store as bytes since replays are opaque.
    private volatile ByteString storedReplay;

    public FetchResponseStreamObserver(ObserverContext subscribe) {
        this.subscribe = subscribe;
    }

    @Override
    public void onNext(FetchResponse fetchResponse) {
        logger.info("Received batch of " + fetchResponse.getEventsList().size() + " events");
        logger.info("RPC ID: " + fetchResponse.getRpcId());
        for(ConsumerEvent ce : fetchResponse.getEventsList()) {
            try {
                storedReplay  = ce.getReplayId();
                subscribe.processEvent(ce);
            } catch (Exception e) {
                logger.info(e.toString());
            }
            receivedEvents.addAndGet(1);
        }
        // Latest replayId stored for any future FetchRequests with CUSTOM ReplayPreset.
        // NOTE: Replay IDs are opaque in nature and should be stored and used as bytes without any conversion.
        storedReplay = fetchResponse.getLatestReplayId();

        // Reset retry count
        if (Subscribe.retriesLeft.get() != Subscribe.MAX_RETRIES) {
            Subscribe.retriesLeft.set(Subscribe.MAX_RETRIES);
        }

        // Implementing a basic flow control strategy where the next fetchRequest is sent only after the
        // requested number of events in the previous fetchRequest(s) are received.
        // NOTE: This block may need to be implemented before the processing of events if event processing takes
        // a long time. There is a 70s timeout period during which, if pendingNumRequested is 0 and no events are
        // further requested then the stream will be closed.
        if (fetchResponse.getPendingNumRequested() == 0) {
            subscribe.fetchMore();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        CommonContext.printStatusRuntimeException("Error during Subscribe", (Exception) throwable);
        logger.info("Retries remaining: " + Subscribe.retriesLeft.get());
        if (Subscribe.retriesLeft.get() == 0) {
            logger.info("Exhausted all retries. Closing Subscription.");
            Subscribe.isActive.set(false);
        } else {
            Subscribe.retriesLeft.decrementAndGet();
            Metadata trailers = ((StatusRuntimeException)throwable).getTrailers() != null ? ((StatusRuntimeException)throwable).getTrailers() : null;
            String errorCode = (trailers != null && trailers.get(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER)) != null) ?
                    trailers.get(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER)) : null;

            // Closing the old stream for sanity
            subscribe.closeFetchRequestStream();

            ReplayPreset retryReplayPreset = ReplayPreset.LATEST;
            ByteString retryReplayId = null;
            long retryDelay = 0;

            // Retry strategies that can be implemented based on the error type.
            if (errorCode.contains(Subscribe.ERROR_REPLAY_ID_VALIDATION_FAILED) || errorCode.contains(Subscribe.ERROR_REPLAY_ID_INVALID)) {
                logger.info("Invalid or no replayId provided in FetchRequest for CUSTOM Replay. Trying again with EARLIEST Replay.");
                retryDelay = subscribe.getBackoffWaitTime();
                retryReplayPreset = ReplayPreset.EARLIEST;
            } else if (errorCode.contains(Subscribe.ERROR_SERVICE_UNAVAILABLE)) {
                logger.info("Service currently unavailable. Trying again with LATEST Replay.");
                retryDelay = Subscribe.SERVICE_UNAVAILABLE_WAIT_BEFORE_RETRY_SECONDS * 1000L;
            } else {
                retryDelay = subscribe.getBackoffWaitTime();
                if (storedReplay != null) {
                    logger.info("Retrying with Stored Replay.");
                    retryReplayPreset = ReplayPreset.CUSTOM;
                    retryReplayId = getStoredReplay();
                } else {
                    logger.info("Retrying with LATEST Replay.");;
                }

            }
            logger.info("Retrying in " + retryDelay + "ms.");
            subscribe.replay(retryReplayPreset, retryReplayId, retryDelay);
        }
    }

    /**
     * General getters and setters.
     */
    public AtomicInteger getReceivedEvents() {
        return receivedEvents;
    }

    public void updateReceivedEvents(int delta) {
        receivedEvents.addAndGet(delta);
    }

    public ByteString getStoredReplay() {
        return storedReplay;
    }

    public void setStoredReplay(ByteString storedReplay) {
        this.storedReplay = storedReplay;
    }

    @Override
    public void onCompleted() {
        logger.info("Call completed by server. Closing Subscription.");
        subscribe.deactivate();
    }
}
