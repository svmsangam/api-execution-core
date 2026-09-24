package com.example.executioncore.domain.serializer;

public interface Serializer {

    <T> String serialize(T object);

    <T> T deserialize(String json, Class<T> targetClass);
}