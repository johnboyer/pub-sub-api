package genericpubsub;

import com.salesforce.eventbus.protobuf.ConsumerEvent;

import java.io.IOException;

public interface ObserverContext {
    void processEvent(ConsumerEvent ce) throws IOException;
    void deactivate();
    void fetchMore();

}
