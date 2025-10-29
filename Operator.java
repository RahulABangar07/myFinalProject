package com.example.filters.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Operator {
    EQUALS("Equals", "=", "eq"),
    NOT_EQUALS("Not Equals", "!=", "ne"),
    GREATER_THAN("Greater Than", ">", "gt"),
    GREATER_THAN_EQUAL("Greater Than Equal", ">=", "ge"),
    LESS_THAN("Less Than", "<", "lt"),
    LESS_THAN_EQUAL("Less Than Equal", "<=", "le"),
    BETWEEN_AND("Between And", "BETWEEN", "between");

    private final String description;
    private final String cosmosOp;
    private final String searchIndexOp;
}
