package com.example.executioncore.storage.lettuce;

import com.example.executioncore.domain.storage.AtomicStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JedisAtomicStore implements AtomicStore {

    private static final String COMPARE_AND_DELETE_RESOURCE = "scripts/compare_and_delete.lua";
    private static final String COMPARE_AND_SET_RESOURCE = "scripts/compare_and_set.lua";

    private final JedisPool jedisPool;
    private final String compareAndDeleteScript;
    private final String compareAndSetScript;

    public JedisAtomicStore(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.compareAndDeleteScript = loadScript(COMPARE_AND_DELETE_RESOURCE);
        this.compareAndSetScript = loadScript(COMPARE_AND_SET_RESOURCE);
    }

    @Override
    public <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType) {
        Objects.requireNonNull(script, "script must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        Objects.requireNonNull(args, "args must not be null");
        Objects.requireNonNull(returnType, "returnType must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(script, keys, args);
            return returnType.cast(result);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        validateKeyValueAndTtl(key, value, ttl);
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().px(ttl.toMillis()));
            return "OK".equals(result);
        }
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        try (Jedis jedis = jedisPool.getResource()) {
            return Optional.ofNullable(jedis.get(key));
        }
    }

    @Override
    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        validateKeyValueAndTtl(key, newValue, ttl);
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                    compareAndSetScript,
                    List.of(key),
                    List.of(expectedValue, newValue, Long.toString(ttl.toMillis())));
            return Long.valueOf(1L).equals(result);
        }
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(compareAndDeleteScript, List.of(key), List.of(expectedValue));
            return Long.valueOf(1L).equals(result);
        }
    }

    private static void validateKeyValueAndTtl(String key, String value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private static String loadScript(String resource) {
        try (InputStream inputStream = JedisAtomicStore.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new UncheckedIOException(new IOException("Missing Lua resource: " + resource));
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load Lua resource: " + resource, exception);
        }
    }
}
