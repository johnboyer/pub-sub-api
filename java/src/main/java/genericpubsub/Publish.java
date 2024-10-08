package genericpubsub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.salesforce.eventbus.protobuf.ProducerEvent;
import com.salesforce.eventbus.protobuf.PublishRequest;
import com.salesforce.eventbus.protobuf.PublishResponse;
import com.salesforce.eventbus.protobuf.PublishResult;
import com.salesforce.eventbus.protobuf.SchemaInfo;
import config.PubSubApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;

import com.google.protobuf.ByteString;
import utility.CommonContext;

/**
 * A single-topic publisher that creates an Order Event event and publishes it. This example uses
 * Pub/Sub API's Publish RPC to publish events.
 *
 * Example:
 * ./run.sh genericpubsub.Publish
 *
 * @author sidd0610
 */
@Slf4j
public class Publish extends CommonContext {

    private final Schema schema;

    public Publish(PubSubApiConfig pubSubApiConfig) {
        super(pubSubApiConfig);
        setupTopicDetails(pubSubApiConfig.getPubsub().getTopic(), true, true);
        schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
    }

    /**
     * Helper function for creating the ProducerEvent to be published
     *
     * @return ProducerEvent
     * @throws IOException
     */
    private ProducerEvent generateProducerEvent() throws IOException {
        Schema schema = new Schema.Parser().parse(schemaInfo.getSchemaJson());
        GenericRecord event = createEventMessage(schema);

        // Convert to byte array
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(event.getSchema());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(buffer, null);
        writer.write(event, encoder);

        return ProducerEvent.newBuilder().setSchemaId(schemaInfo.getSchemaId())
                .setPayload(ByteString.copyFrom(buffer.toByteArray())).build();
    }

    /**
     * Helper function to generate the PublishRequest with the generated ProducerEvent to be sent
     * using the Publish RPC
     *
     * @return PublishRequest
     * @throws IOException
     */
    private PublishRequest generatePublishRequest() throws IOException {
        ProducerEvent e = generateProducerEvent();
        return PublishRequest.newBuilder().setTopicName(busTopicName).addEvents(e).build();
    }

    /**
     * Helper function to publish the event using Publish RPC
     */
    public ByteString publish() throws Exception {
        PublishResponse response = blockingStub.publish(generatePublishRequest());
        return validatePublishResponse(response);
    }

    /**
     * Helper function for other examples to publish the event using Publish RPC
     *
     * @param event
     * @return
     * @throws Exception
     */
    public PublishResponse publish(ProducerEvent event) throws Exception {
        PublishRequest publishRequest = PublishRequest.newBuilder().setTopicName(busTopicName).addEvents(event).build();
        PublishResponse response = blockingStub.publish(publishRequest);
        validatePublishResponse(response);
        return response;
    }

    /**
     * Helper function to validate the PublishResponse received. Also prints the RPC id of the call.
     *
     * @param response
     * @return
     */
    private ByteString validatePublishResponse(PublishResponse response) {
        ByteString lastPublishedReplayId = null;
        List<PublishResult> resultList = response.getResultsList();
        if (resultList.size() != 1) {
            String errorMsg = "[ERROR] Error during Publish, received: " + resultList.size() + " events instead of expected 1";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        } else {
            PublishResult result = resultList.get(0);
            if (result.hasError()) {
                log.error("[ERROR] Publishing batch failed with rpcId: {}", response.getRpcId());
                log.error("[ERROR] Error during Publish, event with correlationKey: {} failed with: {}",
                        response.getResults(0).getCorrelationKey(), result.getError().getMsg());
            } else {
                lastPublishedReplayId = result.getReplayId();
                log.info("Publish Call RPC ID: {}", response.getRpcId());
                log.info("Successfully published an event with correlationKey: {} at {} for tenant {}.",
                        response.getResults(0).getCorrelationKey(), busTopicName, tenantGuid);
            }
        }

        return lastPublishedReplayId;
    }

    public SchemaInfo getSchemaInfo() {
        return schemaInfo;
    }

    public static void main(String[] args) throws IOException {
        PubSubApiConfig pubSubApiConfig = PubSubApiConfig.getPubSubApiConfig();

        // Using the try-with-resource statement. The CommonContext class implements AutoCloseable in
        // order to close the resources used.
        try (Publish example = new Publish(pubSubApiConfig)) {
            example.publish();
        } catch (Exception e) {
            CommonContext.printStatusRuntimeException("Publishing events", e);
        }
    }
}