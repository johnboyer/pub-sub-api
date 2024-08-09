package genericpubsub;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ConsumerEvent;
import com.salesforce.eventbus.protobuf.FetchRequest;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public interface ObserverContext {
    void closeFetchRequestStream();
    void deactivate();
    void fetchMore();
    long getBackoffWaitTime();
    void processEvent(ConsumerEvent ce) throws IOException;
    void replay(ReplayPreset replayPreset, ByteString replayId, long retryDelay);
}
