package com.example.filters.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompositeFilter implements Filter {

    private Logical logical;
    private List<Filter> filters; // may contain Condition or another CompositeFilter
}
