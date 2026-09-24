package com.example.executioncore.domain.ratelimit;

import com.example.executioncore.domain.storage.AtomicStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class SlidingWindowRateLimiter implements RateLimiter{
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

        if (result == null || result.size() < 2) {
            // Fail-safe default: treat unexpected/malformed responses as denied
            return RateLimitResult.denied(capacity, Instant.ofEpochMilli(now + millis));
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        Instant resetTime = Instant.ofEpochMilli(now + millis);

        return allowed
                ? RateLimitResult.allowed(remaining, capacity, resetTime)
                : RateLimitResult.denied(capacity, resetTime);
    }
}
