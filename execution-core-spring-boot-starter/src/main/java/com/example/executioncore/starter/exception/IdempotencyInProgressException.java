package com.example.executioncore.starter.exception;

public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException(String key) {
        super("An execution is already in progress for idempotency key: " + key);
    }
}
