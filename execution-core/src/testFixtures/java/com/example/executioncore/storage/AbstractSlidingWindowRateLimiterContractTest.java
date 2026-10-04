package com.example.executioncore.storage;


import com.example.executioncore.domain.ratelimit.RateLimitResult;
import com.example.executioncore.domain.ratelimit.RateLimiter;
import com.example.executioncore.domain.ratelimit.SlidingWindowRateLimiter;
import com.example.executioncore.domain.storage.AtomicStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractSlidingWindowRateLimiterContractTest {

    protected abstract AtomicStore getStore();
    protected abstract void clearKeys();

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clearKeys();
        rateLimiter = new SlidingWindowRateLimiter(getStore());
    }

    @Test
    @DisplayName("Allows requests within capacity limit")
    void testAllowsRequestsWithinLimit() {
        String key = "rate:test:client1";
        long capacity = 3;
        Duration window = Duration.ofSeconds(10);

        for (int i = 0; i < capacity; i++) {
            RateLimitResult result = rateLimiter.evaluate(key, capacity, window);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Rejects requests exceeding window capacity")
    void testRejectsExceedingRequests() {
        String key = "rate:test:client2";
        long capacity = 2;
        Duration window = Duration.ofSeconds(10);

        RateLimitResult r1 = rateLimiter.evaluate(key, capacity, window);
        assertTrue(r1.isAllowed());

        RateLimitResult r2 = rateLimiter.evaluate(key, capacity, window);
        assertTrue(r2.isAllowed());

        // Third request exceeds limit
        RateLimitResult r3 = rateLimiter.evaluate(key, capacity, window);
        assertFalse(r3.isAllowed(), "Request exceeding capacity must be rejected");
    }

    @Test
    @DisplayName("Allows requests again after sliding window expires")
    void testAllowsRequestsAfterWindowExpiration() throws InterruptedException {
        String key = "rate:test:client3";
        long capacity = 1;
        Duration window = Duration.ofSeconds(1);

        RateLimitResult r1 = rateLimiter.evaluate(key, capacity, window);
        assertTrue(r1.isAllowed());

        RateLimitResult r2 = rateLimiter.evaluate(key, capacity, window);
        assertFalse(r2.isAllowed());

        // Wait for sliding window to expire
        Thread.sleep(1100);

        RateLimitResult r3 = rateLimiter.evaluate(key, capacity, window);
        assertTrue(r3.isAllowed(), "Request should be allowed after window expiration");
    }
}