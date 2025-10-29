package com.example.filters.query;

import com.example.filters.model.*;
import java.util.List;
import java.util.stream.Collectors;

public class AzureSearchIndexQueryBuilder {

    public static String build(FilterRequest filterRequest) {
        if (filterRequest.getCompositeFilter() == null)
            return "";

        return buildFilterExpression(filterRequest.getCompositeFilter());
    }

    private static String buildFilterExpression(Filter filter) {
        if (filter instanceof Condition condition) {
            return buildCondition(condition);
        } else if (filter instanceof CompositeFilter composite) {
            return buildComposite(composite);
        }
        return "";
    }

    private static String buildCondition(Condition condition) {
        String field = condition.getFieldName();
        String op = condition.getOperator().getSearchIndexOp();
        Object val = condition.getValue();

        if (condition.getOperator() == Operator.BETWEEN_AND && val instanceof List<?> range && range.size() == 2) {
            return String.format("(%s ge %s and %s le %s)", field, formatValue(range.get(0)), field, formatValue(range.get(1)));
        }

        if (condition.isArrayField()) {
            // For arrays, use "any" syntax in Azure Search
            return String.format("(%s/any(t: t %s %s))", field, op, formatValue(val));
        }

        return String.format("(%s %s %s)", field, op, formatValue(val));
    }

    private static String buildComposite(CompositeFilter composite) {
        Logical logical = composite.getLogical();
        List<String> expressions = composite.getFilters().stream()
                .map(AzureSearchIndexQueryBuilder::buildFilterExpression)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String joined = String.join(" " + logical.getSearchIndex() + " ", expressions);

        if (logical == Logical.NOT) {
            return "(not (" + joined + "))";
        }

        return "(" + joined + ")";
    }

    private static String formatValue(Object value) {
        if (value instanceof Number)
            return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
