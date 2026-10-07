package com.example.executioncore.storage.lettuce;

import com.example.executioncore.domain.storage.AtomicStore;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LettuceAtomicStore implements AtomicStore {

    private static final String COMPARE_AND_DELETE_RESOURCE = "scripts/compare_and_delete.lua";
    private static final String COMPARE_AND_SET_RESOURCE = "scripts/compare_and_set.lua";

    private final StatefulRedisConnection<String, String> connection;
    private final String compareAndDeleteScript;
    private final String compareAndSetScript;

    public LettuceAtomicStore(StatefulRedisConnection<String, String> connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.compareAndDeleteScript = loadScript(COMPARE_AND_DELETE_RESOURCE);
        this.compareAndSetScript = loadScript(COMPARE_AND_SET_RESOURCE);
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
        try (InputStream inputStream = LettuceAtomicStore.class.getClassLoader().getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new UncheckedIOException(new IOException("Missing Lua resource: " + resource));
            }
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load Lua resource: " + resource, exception);
        }
    }

    @Override
    public <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType) {
        Objects.requireNonNull(script, "script must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        Objects.requireNonNull(args, "args must not be null");
        Objects.requireNonNull(returnType, "returnType must not be null");

        Object result = connection.sync().eval(
                script,
                ScriptOutputType.MULTI,
                keys.toArray(new String[0]),
                args.toArray(new String[0]));
        return returnType.cast(result);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        validateKeyValueAndTtl(key, value, ttl);
        String result = connection.sync().set(key, value, SetArgs.Builder.nx().px(ttl.toMillis()));
        return "OK".equals(result);
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(connection.sync().get(key));
    }

    @Override
    public boolean compareAndSet(String key, String expectedValue, String newValue, Duration ttl) {
        validateKeyValueAndTtl(key, newValue, ttl);
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");
        Long result = connection.sync().eval(
                compareAndSetScript,
                ScriptOutputType.INTEGER,
                new String[]{key},
                expectedValue,
                newValue,
                Long.toString(ttl.toMillis()));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(expectedValue, "expectedValue must not be null");

        Long result = connection.sync().eval(
                compareAndDeleteScript,
                ScriptOutputType.INTEGER,
                new String[]{key},
                expectedValue);
        return Long.valueOf(1L).equals(result);
    }
}
