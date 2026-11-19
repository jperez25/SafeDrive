package com.qnt.safedrive.tests.core.utils;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The one shared Jackson 3 mapper for tests. */
public final class Json {

    public static final JsonMapper MAPPER =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static JsonNode tree(String json) {
        return MAPPER.readTree(json);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        return MAPPER.treeToValue(node, type);
    }
}
