package com.example.executioncore.domain.idempotency;

import java.util.Optional;

public record IdempotencyResult<T>(
        ExecutionState state,
        String lockOwnerToken,
        Optional<T> cachedResponse
) {
    public static <T> IdempotencyResult<T> acquired(String lockOwnerToken) {
        return new IdempotencyResult<>(ExecutionState.ACQUIRED, lockOwnerToken, Optional.empty());
    }

    public static <T> IdempotencyResult<T> inProgress() {
        return new IdempotencyResult<>(ExecutionState.IN_PROGRESS, null, Optional.empty());
    }

    public static <T> IdempotencyResult<T> completed(T response) {
        return new IdempotencyResult<>(ExecutionState.COMPLETED, null, Optional.ofNullable(response));
    }
}