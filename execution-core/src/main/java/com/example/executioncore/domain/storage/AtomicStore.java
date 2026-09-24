package com.example.executioncore.domain.storage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface AtomicStore {

    /**
     * Executes a script atomically against the storage engine.
     */
    <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType);

    /**
     * Attempts to acquire an atomic lock key with a value if the key does not already exist.
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * Retrieves the string value stored at the given key.
     */
    Optional<String> get(String key);

    /**
     * Overwrites a key with a value and updates its TTL.
     */
    void set(String key, String value, Duration ttl);

    /**
     * Removes a key if its current stored value matches the expected value.
     */
    boolean compareAndDelete(String key, String expectedValue);
}