package genericpubsub;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;
import utility.ChangeEventUtils;
import utility.EventParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;


@Slf4j
public class OpportunityChangeEventProcessor {

    private final GenericRecord changeEventMessage;
    private final List<String> changedFields;
    private final List<String> recordIds;

    public OpportunityChangeEventProcessor(GenericRecord changeEventMessage) {
        if (ChangeEventUtils.isOpportunityChangeEvent(changeEventMessage)) {
            this.changeEventMessage = changeEventMessage;
            this.changedFields = ChangeEventUtils.toListField(changeEventMessage, "changeFields");
            this.recordIds = ChangeEventUtils.toListField(changeEventMessage, "recordIds");
        } else {
            throw new IllegalArgumentException("ChangeEventHeader has not an OpportunityChangeEvent'");
        }
    }

    /**
     * Process the changed fields in the <code>Opportunity</code>
     * <a href="https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_opportunity.htm">Opportunity Field Reference</a>
     */
    public void process() {
        if (changedFields.contains("StageName")) {
            String stageName = changeEventMessage.get("StageName").toString();
            log.info("StageName changed to: {}", stageName);
        }
    }
}
