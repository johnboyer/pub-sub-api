package com.salesforce.eventbus;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.apache.avro.generic.GenericRecord;
import utility.ChangeEventUtils;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Value
public class ChangeEventHeader {
    String entityName;
    List<String> recordIds;
    String changeType;
    String changeOrigin;
    String transactionKey;
    Integer sequenceNumber;
    Long commitTimestamp;
    Long commitNumber;
    String commitUser;
    List<String> nulledFields;
    List<String> diffFields;
    List<String> changedFields;

    public static ChangeEventHeader from(@NonNull GenericRecord changeEventMessage) {
        final GenericRecord changeEventHeader = (GenericRecord) changeEventMessage.get("ChangeEventHeader");
        return ChangeEventHeader.builder()
                .entityName(changeEventHeader.get("entityName").toString())
                .recordIds(ChangeEventUtils.toListField(changeEventMessage, "recordIds"))
                .changeType(changeEventHeader.get("changeType").toString())
                .changeOrigin(changeEventHeader.get("changeOrigin").toString())
                .transactionKey(changeEventHeader.get("transactionKey").toString())
                .sequenceNumber((Integer) changeEventHeader.get("sequenceNumber"))
                .commitTimestamp((Long) changeEventHeader.get("commitTimestamp"))
                .commitNumber((Long) changeEventHeader.get("commitNumber"))
                .commitUser(changeEventHeader.get("commitUser").toString())
                .nulledFields(ChangeEventUtils.toListField(changeEventMessage, "nulledFields"))
                .diffFields(ChangeEventUtils.toListField(changeEventMessage, "diffFields"))
                .changedFields(ChangeEventUtils.toListField(changeEventMessage, "changedFields"))
                .build();
    }
}