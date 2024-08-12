package genericpubsub;

import java.io.IOException;

import com.salesforce.eventbus.protobuf.TopicInfo;
import com.salesforce.eventbus.protobuf.TopicRequest;

import lombok.extern.slf4j.Slf4j;
import utility.CommonContext;
import utility.ExampleConfigurations;

/**
 * An example that retrieves the topic info of a single-topic.
 *
 * Example:
 * ./run.sh genericpubsub.GetTopic
 *
 * @author sidd0610
 */
@Slf4j
public class GetTopic extends CommonContext {

    public GetTopic(final ExampleConfigurations options) {
        super(options);
    }

    private void getTopic(String topicName) {
        // Use the GetTopic RPC to get the topic info for the given topicName.
        TopicInfo topicInfo = blockingStub.getTopic(TopicRequest.newBuilder().setTopicName(topicName).build());

        log.info("Topic Details:");
        topicInfo.getAllFields().forEach((key, value) -> log.info("{} : {}", key, value));
    }

    public static void main(String[] args) throws IOException {
        ExampleConfigurations exampleConfigurations = new ExampleConfigurations("arguments.yaml");

        // Using the try-with-resource statement. The CommonContext class implements AutoCloseable in
        // order to close the resources used.
        try (GetTopic example = new GetTopic(exampleConfigurations)) {
            example.getTopic(exampleConfigurations.getTopic());
        } catch (Exception e) {
            printStatusRuntimeException("Error while Getting Topic", e);
        }
    }
}
