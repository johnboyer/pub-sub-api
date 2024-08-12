package com.salesforce.eventbus;


public final class Opportunity {
    /**
     * ForeignKey:EntityId
     */
    private CharSequence accountId;
    /**
     * Data:Boolean
     */
    private Boolean isPrivate;
    /**
     * Data:Text
     */
    private CharSequence name;
    /**
     * Data:StringPlusClob
     */
    private CharSequence description;
    /**
     * Data:DynamicEnum
     */
    private CharSequence stageName;
    /**
     * Data:Currency
     */
    private Double amount;
    /**
     * Data:Percent
     */
    private Double probability;
    /**
     * Data:Currency
     */
    private Double expectedRevenue;
    /**
     * Data:Double
     */
    private Double totalOpportunityQuantity;
    /**
     * Data:DateOnly
     */
    private Long closeDate;
    /**
     * Data:DynamicEnum
     */
    private CharSequence type;
    /**
     * Data:Text
     */
    private CharSequence nextStep;
    /**
     * Data:DynamicEnum
     */
    private CharSequence leadSource;
    /**
     * Data:Boolean
     */
    private Boolean isClosed;
    /**
     * Data:Boolean
     */
    private Boolean isWon;
    /**
     * Data:StaticEnum
     */
    private CharSequence forecastCategory;
    /**
     * Data:DynamicEnum
     */
    private CharSequence forecastCategoryName;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence campaignId;
    /**
     * Data:Boolean
     */
    private Boolean hasOpportunityLineItem;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence pricebook2Id;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence ownerId;
    /**
     * CreatedDate:DateTime
     */
    private Long createdDate;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence createdById;
    /**
     * Data:DateTime
     */
    private Long lastModifiedDate;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence lastModifiedById;
    /**
     * Data:DateTime
     */
    private Long lastStageChangeDate;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence contactId;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence contractId;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence lastAmountChangedHistoryId;
    /**
     * ForeignKey:EntityId
     */
    private CharSequence lastCloseDateChangedHistoryId;
}