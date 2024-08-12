package com.salesforce.sobject;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import org.apache.avro.generic.GenericRecord;

import java.util.Optional;


@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor(force = true)
@Value
public class Opportunity extends BaseObject {
    /**
     * ForeignKey:EntityId
     */
    CharSequence accountId;
    /**
     * Data:Boolean
     */
    Boolean isPrivate;
    /**
     * Data:Text
     */
    CharSequence name;
    /**
     * Data:StringPlusClob
     */
    CharSequence description;
    /**
     * Data:DynamicEnum
     */
    CharSequence stageName;
    /**
     * Data:Currency
     */
    Double amount;
    /**
     * Data:Percent
     */
    Double probability;
    /**
     * Data:Currency
     */
    Double expectedRevenue;
    /**
     * Data:Double
     */
    Double totalOpportunityQuantity;
    /**
     * Data:DateOnly
     */
    Long closeDate;
    /**
     * Data:DynamicEnum
     */
    CharSequence type;
    /**
     * Data:Text
     */
    CharSequence nextStep;
    /**
     * Data:DynamicEnum
     */
    CharSequence leadSource;
    /**
     * Data:Boolean
     */
    Boolean isClosed;
    /**
     * Data:Boolean
     */
    Boolean isWon;
    /**
     * Data:StaticEnum
     */
    CharSequence forecastCategory;
    /**
     * Data:DynamicEnum
     */
    CharSequence forecastCategoryName;
    /**
     * ForeignKey:EntityId
     */
    CharSequence campaignId;
    /**
     * Data:Boolean
     */
    Boolean hasOpportunityLineItem;
    /**
     * ForeignKey:EntityId
     */
    CharSequence pricebook2Id;
    /**
     * ForeignKey:EntityId
     */
    CharSequence ownerId;
    /**
     * Data:DateTime
     */
    Long lastStageChangeDate;
    /**
     * ForeignKey:EntityId
     */
    CharSequence contactId;
    /**
     * ForeignKey:EntityId
     */
    CharSequence contractId;
    /**
     * ForeignKey:EntityId
     */
    CharSequence lastAmountChangedHistoryId;
    /**
     * ForeignKey:EntityId
     */
    CharSequence lastCloseDateChangedHistoryId;

    public static Optional<Opportunity> from(@NonNull GenericRecord genericRecord) {

        return Optional.empty();

    }
}