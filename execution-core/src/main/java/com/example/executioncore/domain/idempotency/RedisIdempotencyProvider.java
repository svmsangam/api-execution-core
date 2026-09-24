package com.example.executioncore.domain.idempotency;

import java.time.Duration;


import com.example.executioncore.domain.serializer.Serializer;
import com.example.executioncore.domain.storage.AtomicStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RedisIdempotencyProvider implements IdempotencyProvider {

    private static final String LOCK_PREFIX = "LOCKED:";

    private final AtomicStore atomicStore;
    private final Serializer serializer;

    public RedisIdempotencyProvider(AtomicStore atomicStore, Serializer serializer) {
        this.atomicStore = Objects.requireNonNull(atomicStore, "AtomicStore must not be null");
        this.serializer = Objects.requireNonNull(serializer, "Serializer must not be null");
    }

    @Override
    public <T> IdempotencyResult<T> process(String key, Duration lockTtl, Class<T> returnType) {
        String lockOwnerToken = UUID.randomUUID().toString();
        String lockValue = LOCK_PREFIX + lockOwnerToken;

        // Try atomic lock acquisition
        boolean acquired = atomicStore.setIfAbsent(key, lockValue, lockTtl);

        if (acquired) {
            return IdempotencyResult.acquired(lockOwnerToken);
        }

        // Lock acquisition failed — inspect current state in storage
        Optional<String> existingValueOpt = atomicStore.get(key);

        if (existingValueOpt.isEmpty()) {
            // Key expired during race window
            return IdempotencyResult.inProgress();
        }

        String existingValue = existingValueOpt.get();

        if (existingValue.startsWith(LOCK_PREFIX)) {
            // Another thread/instance is currently executing this request
            return IdempotencyResult.inProgress();
        }

        // Request was completed previously — deserialize cached payload
        T cachedResponse = serializer.deserialize(existingValue, returnType);
        return IdempotencyResult.completed(cachedResponse);
    }

    @Override
    public <T> void markCompleted(String key, String lockOwnerToken, T response, Duration retentionTtl) {
        String serializedResponse = serializer.serialize(response);
        atomicStore.set(key, serializedResponse, retentionTtl);
    }

    @Override
    public void releaseLock(String key, String lockOwnerToken) {
        String lockValue = LOCK_PREFIX + lockOwnerToken;
        atomicStore.compareAndDelete(key, lockValue);
    }
}