package com.example.executioncore.domain.storage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Secondary Port defining atomic storage operations required by the execution core.
 * <p>
 * Implementations of this interface serve as infrastructure adapters (e.g., Lettuce,
 * Jedis, Spring Data Redis) providing thread-safe, atomic operations over key-value stores.
 *
 * @author subham
 * @version 1.0.0
 */
public interface AtomicStore {

    /**
     * Atomically sets a key to a given value only if the key does not already exist in storage.
     *
     * @param key   the unique storage key
     * @param value the payload or lock token to store
     * @param ttl   the time-to-live duration before automatic expiration; if {@null} or zero, no expiration is applied
     * @return {@code true} if the key was successfully set; {@code false} if the key already existed
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * Retrieves the current string value associated with the specified key.
     *
     * @param key the unique storage key
     * @return an {@link Optional} containing the string value if present; otherwise {@link Optional#empty()}
     */
    Optional<String> get(String key);

    /**
     * Sets a key to a given value, unconditionally overwriting any existing data.
     *
     * @param key   the unique storage key
     * @param value the string value to store
     * @param ttl   the time-to-live duration before automatic expiration
     */
    void set(String key, String value, Duration ttl);

    /**
     * Compares the current value of a key against an expected value and deletes the key
     * if and only if the values match.
     * <p>
     * Implementation must guarantee atomicity (typically via Lua script evaluation) to prevent
     * race conditions during lock releases.
     *
     * @param key           the unique lock key
     * @param expectedValue the owner token expected to currently hold the lock
     * @return {@code true} if the value matched and key was deleted; {@code false} otherwise
     */
    boolean compareAndDelete(String key, String expectedValue);

    /**
     * Executes an arbitrary atomic script (e.g., Lua) against the underlying storage.
     *
     * @param <T>        the expected return type of the script execution result
     * @param script     the raw script content to execute
     * @param keys       the list of keys accessed by the script
     * @param args       the list of argument values passed to the script
     * @param returnType the class type mapping the script output
     * @return the result of script execution cast to {@code T}
     */
    <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType);
}