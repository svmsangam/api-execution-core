package com.example.executioncore.domain.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    /**
     * Evaluates whether an execution request identified by key is allowed.
     *
     * @param key      Unique key identifying the client or rate-limit bucket.
     * @param capacity Maximum permitted tokens/requests in the time window.
     * @param window   Duration of the evaluation window.
     * @return Evaluation result containing allowance status and remaining capacity.
     */
    RateLimitResult evaluate(String key, long capacity, Duration window);
}
