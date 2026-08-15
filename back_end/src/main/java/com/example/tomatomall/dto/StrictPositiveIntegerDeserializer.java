package com.example.tomatomall.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Keeps cart quantity/product identifiers strict without changing JSON parsing for unrelated APIs.
 */
public class StrictPositiveIntegerDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Integer) context.handleUnexpectedToken(
                    Integer.class,
                    parser.currentToken(),
                    parser,
                    "必须使用 JSON 正整数，不能使用字符串、小数或布尔值"
            );
        }
        long value = parser.getLongValue();
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return (Integer) context.handleWeirdNumberValue(
                    Integer.class,
                    parser.getNumberValue(),
                    "整数超出允许范围"
            );
        }
        return (int) value;
    }
}
