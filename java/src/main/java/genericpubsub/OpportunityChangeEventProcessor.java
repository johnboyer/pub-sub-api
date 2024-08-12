package genericpubsub;

import com.salesforce.eventbus.ChangeEventHeader;
import com.salesforce.sobject.Opportunity;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import utility.ChangeEventUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
public class OpportunityChangeEventProcessor {

    private final GenericRecord changeEventMessage;
    private final ChangeEventHeader changeEventHeader;

    public OpportunityChangeEventProcessor(@NonNull GenericRecord changeEventMessage) {

        if (ChangeEventUtils.isOpportunityChangeEvent(changeEventMessage)) {
            this.changeEventMessage = changeEventMessage;
            this.changeEventHeader = ChangeEventHeader.from(changeEventMessage);
        } else {
            throw new IllegalArgumentException("ChangeEventHeader has not an OpportunityChangeEvent'");
        }
    }

    /**
     * Process the changed fields in the <code>Opportunity</code>
     * <a href="https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_opportunity.htm">Opportunity Field Reference</a>
     */
    public void process() {

        final var id = changeEventHeader.getRecordIds().stream()
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unable to process without recordId"));

        final var builder = Opportunity.builder().id(id);
        //Save the changed fields as functions to avoid reflection
        final Map<String, Function<Opportunity, Object>> changedFields = new HashMap<>();

        //TODO: Implement ChangeEventHeader.changeType
        changeEventHeader.getChangedFields().forEach(field -> {

            switch (field) {
                case "StageName":
                    builder.stageName(changeEventMessage.get("StageName").toString());
                    changedFields.put(field, Opportunity::getStageName);
                    break;
                case "Probability":
                    builder.probability((Double) changeEventMessage.get("Probability"));
                    changedFields.put(field, Opportunity::getProbability);
                    break;
                case "ExpectedRevenue":
                    builder.expectedRevenue((Double) changeEventMessage.get("ExpectedRevenue"));
                    changedFields.put(field, Opportunity::getExpectedRevenue);
                    break;
                case "LastModifiedDate":
                    builder.lastModifiedDate((Long) changeEventMessage.get("LastModifiedDate"));
                    changedFields.put(field, Opportunity::getLastModifiedDate);
                    break;
                case "LastStageChangeDate":
                    builder.lastStageChangeDate((Long) changeEventMessage.get("LastStageChangeDate"));
                    changedFields.put(field, Opportunity::getLastStageChangeDate);
                    break;
                default:
                    log.warn("Unknown or unsupported opportunity change field '{}'", field);
            }
        });

        final var opportunity = builder.build();
        String changeSummary = changedFields.entrySet().stream()
                .map(entry -> entry.getKey().substring(0, 1).toLowerCase() + entry.getKey().substring(1)
                        + "=" + entry.getValue().apply(opportunity))
                .collect(Collectors.joining("\n"));
            log.info("Opportunity changes: \n{}", changeSummary);
    }
}
