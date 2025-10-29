package com.example.filters.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Logical {
    AND("Logical AND", "AND", "AND"),
    OR("Logical OR", "OR", "OR"),
    NOT("Logical NOT", "NOT", "NOT");

    private final String description;
    private final String cosmos;
    private final String searchIndex;
}
