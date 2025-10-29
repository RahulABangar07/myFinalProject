package com.example.filters.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"  // required field in JSON
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Condition.class, name = "condition"),
        @JsonSubTypes.Type(value = CompositeFilter.class, name = "composite")
})
public interface Filter {
}
