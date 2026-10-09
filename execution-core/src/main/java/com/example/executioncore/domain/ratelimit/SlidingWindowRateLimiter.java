package com.example.executioncore.domain.ratelimit;

import com.example.executioncore.domain.storage.AtomicStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class SlidingWindowRateLimiter implements RateLimiter{
    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private final AtomicStore atomicStore;
    private final String luaScript;

    private static final String SLIDING_WINDOW_SCRIPT = "scripts/sliding_window_rate_limiter.lua";

    public SlidingWindowRateLimiter(AtomicStore store){
        if(store == null){
            throw new IllegalArgumentException("Atomic store must not be null");
        }
        this.atomicStore = store;

        try(InputStream in = getClass().getClassLoader().getResourceAsStream(SLIDING_WINDOW_SCRIPT)){
            if (in == null){
                throw new IllegalArgumentException("Resource not found: " + SLIDING_WINDOW_SCRIPT);
            }
            this.luaScript = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }catch (IOException e){
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RateLimitResult evaluate(String key, long capacity, Duration window) {
        log.debug("Executing sliding window rate limit evaluation for key='{}'", key);

        long now = Instant.now().toEpochMilli();
        long millis = window.toMillis();
        long ttl = Math.max(1, window.getSeconds());

        List<String> keys = Collections.singletonList(key);
        List<String> args = List.of(
                String.valueOf(now),
                String.valueOf(millis),
                String.valueOf(capacity),
                String.valueOf(ttl)
        );

        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) atomicStore.executeScript(luaScript, keys, args, List.class);

        Instant resetTime = Instant.ofEpochMilli(now + millis);
        if (result == null
                || result.size() < 2
                || result.get(0) == null
                || result.get(1) == null) {
            // Fail-safe default: treat unexpected/malformed responses as denied
            log.info("Rate limit check FAILED for key='{}' (currentCount={}/{}, window={}s)",
                    key, capacity, capacity, window.getSeconds());
            return RateLimitResult.denied(capacity, resetTime);
        }

        boolean allowed = Long.valueOf(1L).equals(result.get(0));
        long remaining = result.get(1);
        if (allowed && (remaining < 0 || remaining > capacity)) {
            log.info("Rate limit check FAILED for key='{}' (currentCount={}/{}, window={}s)",
                    key, capacity, capacity, window.getSeconds());
            return RateLimitResult.denied(capacity, resetTime);
        }

        if (allowed) {
            long currentCount = capacity - remaining;
            log.info("Rate limit check PASSED for key='{}' (currentCount={}/{}, remaining={})",
                    key, currentCount, capacity, remaining);
            return RateLimitResult.allowed(remaining, capacity, resetTime);
        }

        log.info("Rate limit check FAILED for key='{}' (currentCount={}/{}, window={}s)",
                key, capacity, capacity, window.getSeconds());
        return RateLimitResult.denied(capacity, resetTime);
    }
}
