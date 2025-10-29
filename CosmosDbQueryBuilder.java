package com.example.filters.query;

import com.example.filters.model.*;
import java.util.List;
import java.util.stream.Collectors;

public class CosmosDbQueryBuilder {

    public static String build(FilterRequest filterRequest) {
        if (filterRequest.getCompositeFilter() == null)
            return "";

        String whereClause = buildFilterExpression(filterRequest.getCompositeFilter());
        return String.format("SELECT * FROM c WHERE %s", whereClause);
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
        String field = "c." + condition.getFieldName();
        String op = condition.getOperator().getCosmosOp();

        Object val = condition.getValue();

        if (condition.getOperator() == Operator.BETWEEN_AND && val instanceof List<?> range && range.size() == 2) {
            return String.format("(%s BETWEEN %s AND %s)", field, formatValue(range.get(0)), formatValue(range.get(1)));
        }

        if (condition.isArrayField()) {
            // Cosmos ARRAY_CONTAINS syntax
            return String.format("ARRAY_CONTAINS(%s, %s)", field, formatValue(val));
        }

        return String.format("(%s %s %s)", field, op, formatValue(val));
    }

    private static String buildComposite(CompositeFilter composite) {
        Logical logical = composite.getLogical();
        List<String> expressions = composite.getFilters().stream()
                .map(CosmosDbQueryBuilder::buildFilterExpression)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String joined = String.join(" " + logical.getCosmos() + " ", expressions);

        // NOT operator case
        if (logical == Logical.NOT) {
            return "(NOT (" + joined + "))";
        }

        return "(" + joined + ")";
    }

    private static String formatValue(Object value) {
        if (value instanceof Number)
            return value.toString();
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
