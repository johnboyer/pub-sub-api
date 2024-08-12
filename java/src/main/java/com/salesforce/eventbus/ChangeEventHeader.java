package com.salesforce.eventbus;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

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
}