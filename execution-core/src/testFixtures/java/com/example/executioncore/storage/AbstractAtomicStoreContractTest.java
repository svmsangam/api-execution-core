package com.example.executioncore.storage;

import com.example.executioncore.domain.storage.AtomicStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractAtomicStoreContractTest {

    protected abstract AtomicStore getStore();
    protected abstract void clearKeys();

    @BeforeEach
    void setUp() {
        clearKeys();
    }

    @Test
    @DisplayName("compareAndSet updates a key only when the expected value matches")
    void testCompareAndSet() {
        AtomicStore store = getStore();
        assertTrue(store.setIfAbsent("test:key:1", "lock-token", Duration.ofMinutes(1)));
        assertFalse(store.compareAndSet("test:key:1", "wrong-token", "wrong-value", Duration.ofMinutes(1)));
        assertEquals("lock-token", store.get("test:key:1").orElseThrow());

        assertTrue(store.compareAndSet("test:key:1", "lock-token", "hello-world", Duration.ofMinutes(1)));
        Optional<String> val = store.get("test:key:1");
        assertTrue(val.isPresent());
        assertEquals("hello-world", val.get());
    }

    @Test
    @DisplayName("setIfAbsent succeeds only on new key")
    void testSetIfAbsent() {
        AtomicStore store = getStore();

        boolean created = store.setIfAbsent("test:key:nx", "v1", Duration.ofMinutes(1));
        assertTrue(created);

        boolean duplicate = store.setIfAbsent("test:key:nx", "v2", Duration.ofMinutes(1));
        assertFalse(duplicate);

        assertEquals("v1", store.get("test:key:nx").orElseThrow());
    }

    @Test
    @DisplayName("compareAndDelete removes key only when expected value matches")
    void testCompareAndDelete() {
        AtomicStore store = getStore();
        store.setIfAbsent("test:key:cad", "token-123", Duration.ofMinutes(1));

        // Wrong token fails
        boolean wrongDelete = store.compareAndDelete("test:key:cad", "wrong-token");
        assertFalse(wrongDelete);
        assertTrue(store.get("test:key:cad").isPresent());

        // Correct token succeeds
        boolean correctDelete = store.compareAndDelete("test:key:cad", "token-123");
        assertTrue(correctDelete);
        assertFalse(store.get("test:key:cad").isPresent());
    }
}