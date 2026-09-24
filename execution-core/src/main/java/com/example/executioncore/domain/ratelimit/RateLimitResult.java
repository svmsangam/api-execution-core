package com.example.executioncore.domain.ratelimit;

import java.time.Instant;

public record RateLimitResult(
        boolean isAllowed,
        long remainingTokens,
        long capacity,
        Instant resetTime
) {
    public static RateLimitResult allowed(long remainingTokens, long capacity, Instant resetTime) {
        return new RateLimitResult(true, remainingTokens, capacity, resetTime);
    }

    public static RateLimitResult denied(long capacity, Instant resetTime) {
        return new RateLimitResult(false, 0L, capacity, resetTime);
    }
}