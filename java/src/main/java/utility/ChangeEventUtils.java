package utility;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChangeEventUtils {

    /**
     * Indicates if the message is an <<code>&lt;SObject&gt;ChangeEvent</code>
     * @param changeEventMessage The <code>SObjectChangeEvent</code>
     * <a href="https://developer.salesforce.com/docs/atlas.en-us.change_data_capture.meta/change_data_capture/cdc_message_structure.htm">Change Event Structure</a>
     */
    public static boolean isChangeEvent(GenericRecord changeEventMessage) {
        return changeEventMessage.hasField("ChangeEventHeader");
    }

    /**
     * Indicates if the <code>&lt;SObject&gt;ChangeEvent</code> is an <code>OpportunityChangeEvent</code>
     * @param changeEventMessage The <code>SObjectChangeEvent</code>
     * <a href="https://developer.salesforce.com/docs/atlas.en-us.change_data_capture.meta/change_data_capture/cdc_event_fields_header.htm">ChangeEventHeader Fields</a>
     */
    public static boolean isOpportunityChangeEvent(GenericRecord changeEventMessage) {
        return isChangeEvent(changeEventMessage) && Optional.of(changeEventMessage.get("ChangeEventHeader"))
                .map(GenericRecord.class::cast)
                // The entityName is The API name of the standard or custom object that the change pertains to.
                // For example, Account or MyObject__c.
                .map(record -> record.get("entityName"))
                .map(Object::toString)
                .filter(entityName -> entityName.equals("Opportunity"))
                .isPresent();
    }

    /**
     * Indicates if the <code>&lt;SObject&gt;ChangeEvent</code> is an <code>OpportunityChangeEvent</code>
     * @param changeEventMessage The <code>SObjectChangeEvent</code>
     * <a href="https://developer.salesforce.com/docs/atlas.en-us.change_data_capture.meta/change_data_capture/cdc_event_fields_header.htm">ChangeEventHeader Fields</a>
     */
    public static GenericRecord getChangeEventHeader(GenericRecord changeEventMessage) {
        //TODO: Return Optional and fix documentation
        return (GenericRecord) changeEventMessage.get("ChangeEventHeader");
    }

    public static List<String> toListField(GenericRecord changeEventMessage, String fieldName) {
        return Optional.of(changeEventMessage)
                .map(ChangeEventUtils::getChangeEventHeader)
                .filter(header -> header instanceof GenericData.Record)
                .map(GenericData.Record.class::cast)
                .map(record -> {
                    try {
                        return EventParser.getFieldListFromBitmap(changeEventMessage.getSchema(), record, fieldName);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Error parsing " + fieldName, e);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("ChangeEventHeader is not an GenericData.Record"));
    }

}
