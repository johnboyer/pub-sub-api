package genericpubsub;

import java.io.IOException;

import config.PubSubApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;

import com.salesforce.eventbus.protobuf.SchemaInfo;
import com.salesforce.eventbus.protobuf.SchemaRequest;
import com.salesforce.eventbus.protobuf.TopicInfo;
import com.salesforce.eventbus.protobuf.TopicRequest;

import utility.CommonContext;

/**
 * An example that retrieves the Schema of a single-topic.
 *
 * Example:
 * ./run.sh genericpubsub.GetSchema
 *
 * @author sidd0610
 */
@Slf4j
public class GetSchema extends CommonContext {

    public GetSchema(final PubSubApiConfig options) {
        super(options);
    }

    private void getSchema(String topicName) {
        // Use the GetTopic RPC to get the topic info for the given topicName.
        // Used to retrieve the schema id in this example.
        TopicInfo topicInfo = blockingStub.getTopic(TopicRequest.newBuilder().setTopicName(topicName).build());
        log.info("GetTopic Call RPC ID: {}", topicInfo.getRpcId());

        topicInfo.getAllFields().forEach((key, value) -> log.info("{} : {}", key, value));

        SchemaRequest schemaRequest = SchemaRequest.newBuilder().setSchemaId(topicInfo.getSchemaId()).build();

        // Use the GetSchema RPC to get the schema info of the topic.
        SchemaInfo schemaResponse = blockingStub.getSchema(schemaRequest);
        log.info("GetSchema Call RPC ID: {}", schemaResponse.getRpcId());
        Schema schema = new Schema.Parser().parse(schemaResponse.getSchemaJson());

        // Printing the topic schema
        log.info("Schema of topic  {}: {}", topicName, schema.toString(true));
    }

    public static void main(String[] args) throws IOException {
        PubSubApiConfig pubSubApiConfig = PubSubApiConfig.getPubSubApiConfig();

        // Using the try-with-resource statement. The CommonContext class implements AutoCloseable in
        // order to close the resources used.
        try (GetSchema getSchema = new GetSchema(pubSubApiConfig)) {
            getSchema.getSchema(pubSubApiConfig.getPubsub().getTopic());
        } catch (Exception e) {
            printStatusRuntimeException("Getting schema", e);
        }
    }

}
