package com.example.executioncore.domain.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;

/**
 * Jackson-based implementation of {@link Serializer}.
 */
public class JacksonSerializer implements Serializer {

    private final ObjectMapper objectMapper;

    /**
     * Creates a serializer backed by a default {@link ObjectMapper}.
     */
    public JacksonSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a serializer backed by the supplied {@link ObjectMapper}.
     *
     * @param objectMapper mapper to use for serialization and deserialization
     */
    public JacksonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UncheckedIOException if the object cannot be serialized as JSON
     */
    @Override
    public <T> String serialize(T object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws UncheckedIOException if the JSON cannot be deserialized into the target type
     */
    @Override
    public <T> T deserialize(String json, Class<T> targetClass) {
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize JSON to " + targetClass.getName(), e);
        }
    }
}
