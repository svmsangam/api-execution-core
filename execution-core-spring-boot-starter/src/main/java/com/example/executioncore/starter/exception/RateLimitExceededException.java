package com.example.executioncore.starter.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final String key;

    public RateLimitExceededException(String key) {
        super("Rate limit exceeded for key: " + key);
        this.key = key;
    }

    public RateLimitExceededException(String message, String key) {
        super(message);
        this.key = key;
    }

}
