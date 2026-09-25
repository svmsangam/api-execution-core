package com.example.executioncore.domain.serializer;

/**
 * Defines operations for converting objects to and from serialized text.
 */
public interface Serializer {

    /**
     * Serializes an object to its textual representation.
     *
     * @param object object to serialize
     * @param <T>    type of the object
     * @return serialized representation of the object
     */
    <T> String serialize(T object);

    /**
     * Deserializes text into an object of the requested type.
     *
     * @param json       serialized object
     * @param targetClass class to deserialize the object into
     * @param <T>        type of the deserialized object
     * @return deserialized object
     */
    <T> T deserialize(String json, Class<T> targetClass);
}