package com.example.filters.json;

import com.example.filters.model.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class FilterDeserializer extends JsonDeserializer<Filter> {

    @Override
    public Filter deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        ObjectNode node = mapper.readTree(jp);

        // If it has "filters" and "logical", it's a CompositeFilter
        if (node.has("filters") && node.has("logical")) {
            return mapper.treeToValue(node, CompositeFilter.class);
        }
        // Otherwise, treat it as a Condition
        return mapper.treeToValue(node, Condition.class);
    }
}
