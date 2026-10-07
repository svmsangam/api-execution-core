package com.example.executioncore.storage.springdata;

import com.example.executioncore.domain.storage.AtomicStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SpringDataRedisAtomicStore implements AtomicStore {

    private static final String COMPARE_AND_DELETE_RESOURCE = "scripts/compare_and_delete.lua";
    private static final String COMPARE_AND_SET_RESOURCE = "scripts/compare_and_set.lua";

    private final StringRedisTemplate stringRedisTemplate;
    private final String compareAndDeleteScript;
    private final String compareAndSetScript;

    public SpringDataRedisAtomicStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = Objects.requireNonNull(
                stringRedisTemplate, "stringRedisTemplate must not be null");
        this.compareAndDeleteScript = loadScript(COMPARE_AND_DELETE_RESOURCE);
        this.compareAndSetScript = loadScript(COMPARE_AND_SET_RESOURCE);
    }

    @Override
    public <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType) {
        Objects.requireNonNull(script, "script must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        Objects.requireNonNull(args, "args must not be null");
        Objects.requireNonNull(returnType, "returnType must not be null");

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        List<?> result = stringRedisTemplate.execute(redisScript, keys, args.toArray(new Object[0]));
        return returnType.cast(result);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        validateKeyValueAndTtl(key, value, ttl);
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key));
    }

    @Override
    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        validateKeyValueAndTtl(key, newValue, ttl);
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(compareAndSetScript, Long.class);
        Long result = stringRedisTemplate.execute(
                redisScript,
                List.of(key),
                expectedValue,
                newValue,
                Long.toString(ttl.toMillis()));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(compareAndDeleteScript, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, List.of(key), expectedValue);
        return Long.valueOf(1L).equals(result);
    }

    private static void validateKeyValueAndTtl(String key, String value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private static String loadScript(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load Lua resource: " + resourcePath, exception);
        }
    }
}
