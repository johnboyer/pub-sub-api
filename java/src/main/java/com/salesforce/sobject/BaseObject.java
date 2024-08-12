package com.salesforce.sobject;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SObject system fields
 * <a href="https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/system_fields.htm">System Fields</a></a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor(force = true)
@Getter
public class BaseObject {
    final String id;
    final Boolean isDeleted;
    final String createdById;
    final Long createdDate;
    final String lastModifiedById;
    final Long lastModifiedDate;
    final Long systemModstamp;
}
