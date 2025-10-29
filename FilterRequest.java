package com.example.filters.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.cosmos.core.mapping.Container;
import org.springframework.data.cosmos.core.mapping.PartitionKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Container(containerName = "filterRequests")
public class FilterRequest {

    @Id
    private String id;

    @PartitionKey
    private String number;

    private String name;
    private String description;
    private String creationDate;

    private CompositeFilter compositeFilter;
}
