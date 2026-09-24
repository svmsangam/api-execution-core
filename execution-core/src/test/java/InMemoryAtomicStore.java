import com.example.executioncore.domain.storage.AtomicStore;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class InMemoryAtomicStore implements AtomicStore {

    // Simulates Redis ZSET storage: Key -> Set of epoch millisecond timestamps
    private final Map<String, List<Long>> zSetStore = new ConcurrentHashMap<>();
    // Simulates key-value string storage for locks and results
    private final Map<String, String> stringStore = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T executeScript(String script, List<String> keys, List<String> args, Class<T> returnType) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("At least one key must be provided.");
        }

        String key = keys.get(0);
        long now = Long.parseLong(args.get(0));
        long windowMs = Long.parseLong(args.get(1));
        long capacity = Long.parseLong(args.get(2));

        // Synchronize per key to guarantee atomicity during local unit testing
        synchronized (this) {
            List<Long> timestamps = zSetStore.computeIfAbsent(key, k -> new java.util.ArrayList<>());

            // 1. ZREMRANGEBYSCORE key -inf (now - windowMs)
            long clearBefore = now - windowMs;
            timestamps.removeIf(ts -> ts <= clearBefore);

            // 2. ZCARD key
            long currentCount = timestamps.size();

            // 3. Conditional ZADD
            if (currentCount < capacity) {
                timestamps.add(now);
                long remaining = capacity - currentCount - 1;
                return (T) List.of(1L, remaining); // Allowed
            } else {
                return (T) List.of(0L, 0L); // Denied
            }
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        synchronized (this) {
            return stringStore.putIfAbsent(key, value) == null;
        }
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(stringStore.get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        stringStore.put(key, value);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        synchronized (this) {
            if (expectedValue.equals(stringStore.get(key))) {
                stringStore.remove(key);
                return true;
            }
            return false;
        }
    }

    public void clear() {
        zSetStore.clear();
        stringStore.clear();
    }
}