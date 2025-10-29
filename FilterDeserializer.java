package com.example.filters.json;

import com.example.filters.model.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FilterDeserializer extends JsonDeserializer<Filter> {

    @Override
    public Filter deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);

        // CompositeFilter has "filters" and "logical"
        if (node.has("filters") && node.has("logical")) {
            CompositeFilter composite = new CompositeFilter();
            composite.setLogical(Logical.valueOf(node.get("logical").asText()));

            List<Filter> filterList = new ArrayList<>();
            for (JsonNode child : node.get("filters")) {
                // Recursively parse child filters
                filterList.add(mapper.treeToValue(child, Filter.class));
            }
            composite.setFilters(filterList);
            return composite;
        }

        // Otherwise, treat it as a Condition
        Condition condition = new Condition();
        condition.setFieldName(node.get("fieldName").asText());
        condition.setFieldDisplayName(node.path("fieldDisplayName").asText(null));
        condition.setOperator(Operator.valueOf(node.get("operator").asText()));
        condition.setArrayField(node.path("isArrayField").asBoolean(false));

        // handle value: string, number, or array
        JsonNode valueNode = node.get("value");
        if (valueNode != null) {
            if (valueNode.isArray()) {
                condition.setValue(mapper.convertValue(valueNode, List.class));
            } else if (valueNode.isNumber()) {
                condition.setValue(valueNode.numberValue());
            } else {
                condition.setValue(valueNode.asText());
            }
        }

        return condition;
    }
}
