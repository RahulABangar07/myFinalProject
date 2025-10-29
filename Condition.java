package com.example.filters.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Condition implements Filter {

    private String fieldName;
    private String fieldDisplayName;
    private Operator operator;
    private Object value;
    private boolean isArrayField;
}
