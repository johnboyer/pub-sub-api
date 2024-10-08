package genericpubsub;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import utility.CommonContext;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class FetchResponseStreamObserver implements StreamObserver<FetchResponse> {
    private final ObserverContext observerContext;

    /**
     * -- GETTER --
     *  General getters and setters.
     */
    @Getter
    private final AtomicInteger receivedEvents = new AtomicInteger(0);
    // Replay should be stored in replay store as bytes since replays are opaque.
    @Getter
    private volatile ByteString storedReplay;

    public FetchResponseStreamObserver(ObserverContext observerContext) {
        this.observerContext = observerContext;
    }

    @Override
    public void onNext(FetchResponse fetchResponse) {
        log.info("Received batch of {} events", fetchResponse.getEventsList().size());
        log.info("RPC ID: {}", fetchResponse.getRpcId());
        for(ConsumerEvent ce : fetchResponse.getEventsList()) {
            try {
                storedReplay  = ce.getReplayId();
                observerContext.processEvent(ce);
            } catch (Exception e) {
                log.info(e.toString());
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
            observerContext.fetchMore();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        CommonContext.printStatusRuntimeException("Error during Subscribe", (Exception) throwable);
        log.info("Retries remaining: {}", Subscribe.retriesLeft.get());
        if (Subscribe.retriesLeft.get() == 0) {
            log.info("Exhausted all retries. Closing Subscription.");
            Subscribe.isActive.set(false);
        } else {
            Subscribe.retriesLeft.decrementAndGet();
            Metadata trailers = ((StatusRuntimeException)throwable).getTrailers() != null ? ((StatusRuntimeException)throwable).getTrailers() : null;
            String errorCode = (trailers != null && trailers.get(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER)) != null) ?
                    trailers.get(Metadata.Key.of("error-code", Metadata.ASCII_STRING_MARSHALLER)) : null;

            // Closing the old stream for sanity
            observerContext.closeFetchRequestStream();

            ReplayPreset retryReplayPreset = ReplayPreset.LATEST;
            ByteString retryReplayId = null;
            long retryDelay = 0;

            // Retry strategies that can be implemented based on the error type.
            if (errorCode.contains(Subscribe.ERROR_REPLAY_ID_VALIDATION_FAILED) || errorCode.contains(Subscribe.ERROR_REPLAY_ID_INVALID)) {
                log.info("Invalid or no replayId provided in FetchRequest for CUSTOM Replay. Trying again with EARLIEST Replay.");
                retryDelay = observerContext.getBackoffWaitTime();
                retryReplayPreset = ReplayPreset.EARLIEST;
            } else if (errorCode.contains(Subscribe.ERROR_SERVICE_UNAVAILABLE)) {
                log.info("Service currently unavailable. Trying again with LATEST Replay.");
                retryDelay = Subscribe.SERVICE_UNAVAILABLE_WAIT_BEFORE_RETRY_SECONDS * 1000L;
            } else {
                retryDelay = observerContext.getBackoffWaitTime();
                if (storedReplay != null) {
                    log.info("Retrying with Stored Replay.");
                    retryReplayPreset = ReplayPreset.CUSTOM;
                    retryReplayId = getStoredReplay();
                } else {
                    log.info("Retrying with LATEST Replay.");;
                }

            }
            log.info("Retrying in {}ms.", retryDelay);
            observerContext.replay(retryReplayPreset, retryReplayId, retryDelay);
        }
    }

    public void updateReceivedEvents(int delta) {
        receivedEvents.addAndGet(delta);
    }

    public void setStoredReplay(ByteString storedReplay) {
        this.storedReplay = storedReplay;
    }

    @Override
    public void onCompleted() {
        log.info("Call completed by server. Closing Subscription.");
        observerContext.deactivate();
    }
}
