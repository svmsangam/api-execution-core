package com.example.executioncore.domain.idempotency;

import java.time.Duration;

/**
 * Primary domain engine managing request idempotency execution lifecycle.
 * <p>
 * Handles atomic lock acquisition, in-progress detection, and cached payload retrieval
 * for duplicate incoming requests.
 */
public interface IdempotencyProvider {

    /**
     * Attempts to acquire an execution lock or retrieve an existing result.
     *
     * @param key         Unique idempotency request identifier.
     * @param lockTtl     Safety duration for lock auto-expiration (prevents deadlocks on crashes).
     * @param returnType  Target Class type for deserializing cached results if completed.
     */
    <T> IdempotencyResult<T> process(String key, Duration lockTtl, Class<T> returnType);

    /**
     * Stores the final result of an execution and transitions the key to COMPLETED.
     *
     * @param key            Unique idempotency request identifier.
     * @param lockOwnerToken The UUID token obtained during lock acquisition.
     * @param response       The execution response payload to cache.
     * @param retentionTtl   Duration to retain the cached result in storage.
     */
    <T> void markCompleted(String key, String lockOwnerToken, T response, Duration retentionTtl);

    /**
     * Safely releases an acquired lock if execution fails, enabling client retries.
     *
     * @param key            Unique idempotency request identifier.
     * @param lockOwnerToken The UUID token obtained during lock acquisition.
     */
    void releaseLock(String key, String lockOwnerToken);
}