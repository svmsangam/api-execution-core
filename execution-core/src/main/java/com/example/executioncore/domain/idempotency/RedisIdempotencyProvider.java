package com.example.executioncore.domain.idempotency;

import com.example.executioncore.domain.serializer.Serializer;
import com.example.executioncore.domain.storage.AtomicStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RedisIdempotencyProvider implements IdempotencyProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyProvider.class);
    private static final String LOCK_PREFIX = "LOCKED:";

    private final AtomicStore atomicStore;
    private final Serializer serializer;
    private final String keyPrefix;

    public RedisIdempotencyProvider(AtomicStore atomicStore, Serializer serializer, String keyPrefix) {
        this.atomicStore = Objects.requireNonNull(atomicStore, "AtomicStore must not be null");
        this.serializer = Objects.requireNonNull(serializer, "Serializer must not be null");
        this.keyPrefix = keyPrefix != null ? keyPrefix : "";
    }

    @Override
    public <T> IdempotencyResult<T> process(String key, Duration lockTtl, Class<T> returnType) {
        String fullKey = buildKey(key);
        String lockOwnerToken = UUID.randomUUID().toString();
        String lockValue = LOCK_PREFIX + lockOwnerToken;

        log.debug("Evaluating idempotency for key='{}'", fullKey);

        // 1. Try atomic lock acquisition
        boolean acquired = atomicStore.setIfAbsent(fullKey, lockValue, lockTtl);

        if (acquired) {
            log.info("Acquired execution lock for key='{}' with token='{}'", fullKey, lockOwnerToken);
            return IdempotencyResult.acquired(lockOwnerToken);
        }

        // 2. Lock failed — inspect current state in storage
        Optional<String> existingValueOpt = atomicStore.get(fullKey);

        if (existingValueOpt.isEmpty()) {
            log.warn("Key '{}' expired during race condition window", fullKey);
            return IdempotencyResult.inProgress();
        }

        String existingValue = existingValueOpt.get();

        if (existingValue.startsWith(LOCK_PREFIX)) {
            log.info("Request currently in progress for key='{}'", fullKey);
            return IdempotencyResult.inProgress();
        }

        // 3. Request completed previously — deserialize cached payload
        log.info("Idempotency cache HIT for key='{}'", fullKey);
        T cachedResponse = serializer.deserialize(existingValue, returnType);
        return IdempotencyResult.completed(cachedResponse);
    }

    @Override
    public <T> void markCompleted(String key, String lockOwnerToken, T response, Duration retentionTtl) {
        String fullKey = buildKey(key);
        String expectedLockValue = LOCK_PREFIX + lockOwnerToken;

        String serializedResponse = serializer.serialize(response);
        log.debug("Committing completed response for key='{}': {}", fullKey, serializedResponse);

        // Overwrite key with serialized payload
        atomicStore.set(fullKey, serializedResponse, retentionTtl);
        log.info("Successfully cached response for idempotency key='{}' (TTL={})", fullKey, retentionTtl);
    }

    @Override
    public void releaseLock(String key, String lockOwnerToken) {
        String fullKey = buildKey(key);
        String lockValue = LOCK_PREFIX + lockOwnerToken;
        log.info("Releasing lock for key='{}'", fullKey);
        atomicStore.compareAndDelete(fullKey, lockValue);
    }

    private String buildKey(String userKey) {
        return keyPrefix + userKey;
    }
}