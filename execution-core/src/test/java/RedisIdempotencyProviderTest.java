

import com.example.executioncore.domain.idempotency.ExecutionState;
import com.example.executioncore.domain.idempotency.IdempotencyResult;
import com.example.executioncore.domain.idempotency.RedisIdempotencyProvider;
import com.example.executioncore.domain.serializer.JacksonSerializer;
import com.example.executioncore.domain.serializer.Serializer;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisIdempotencyProviderTest {

    private InMemoryAtomicStore atomicStore;
    private RedisIdempotencyProvider idempotencyProvider;

    private static final String KEY_PREFIX = "sample:idempotency:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration RETENTION_TTL = Duration.ofHours(1);

    // Dummy DTO for testing serialization
    record PaymentResponse(String paymentId, String status, double amount) {}

    @BeforeEach
    void setUp() {
        atomicStore = new InMemoryAtomicStore();
        idempotencyProvider = new RedisIdempotencyProvider(atomicStore, new JacksonSerializer(),KEY_PREFIX);
    }

    @Test
    @DisplayName("First request should acquire execution lock successfully")
    void shouldAcquireLockOnFirstRequest() {
        String key = "idempotency:req_101";

        IdempotencyResult<PaymentResponse> result = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);

        assertEquals(ExecutionState.ACQUIRED, result.state());
        assertNotNull(result.lockOwnerToken());
        assertTrue(result.cachedResponse().isEmpty());

        // Verify underlying storage received the prefixed key
        assertTrue(atomicStore.get(KEY_PREFIX + key).isPresent());
    }

    @Test
    @DisplayName("Concurrent request for active key should return IN_PROGRESS")
    void shouldReturnInProgressForConcurrentRequest() {
        String key = "idempotency:req_102";

        // First thread acquires lock
        IdempotencyResult<PaymentResponse> firstResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.ACQUIRED, firstResult.state());

        // Second concurrent thread attempts same key
        IdempotencyResult<PaymentResponse> secondResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.IN_PROGRESS, secondResult.state());
        assertTrue(secondResult.cachedResponse().isEmpty());
    }

    @Test
    @DisplayName("Subsequent request after completion should return cached response payload")
    void shouldReturnCachedPayloadWhenCompleted() {
        String key = "idempotency:req_103";

        // 1. First request acquires lock
        IdempotencyResult<PaymentResponse> firstResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.ACQUIRED, firstResult.state());

        // 2. Application executes and marks request completed with payload
        PaymentResponse originalResponse = new PaymentResponse("pay_999", "SUCCESS", 150.00);
        idempotencyProvider.markCompleted(key, firstResult.lockOwnerToken(), originalResponse, RETENTION_TTL);

        // 3. Duplicate request arrives later
        IdempotencyResult<PaymentResponse> duplicateResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);

        assertEquals(ExecutionState.COMPLETED, duplicateResult.state());
        assertTrue(duplicateResult.cachedResponse().isPresent());
        assertEquals("pay_999", duplicateResult.cachedResponse().get().paymentId());
        assertEquals("SUCCESS", duplicateResult.cachedResponse().get().status());
        assertEquals(150.00, duplicateResult.cachedResponse().get().amount());
    }

    @Test
    @DisplayName("Cached responses preserve parameterized generic types")
    void shouldReturnCachedParameterizedPayload() {
        String key = "idempotency:req_generic";
        TypeReference<List<PaymentResponse>> responseType = new TypeReference<>() {};

        IdempotencyResult<List<PaymentResponse>> acquired =
                idempotencyProvider.process(key, LOCK_TTL, responseType.getType());
        List<PaymentResponse> original = List.of(new PaymentResponse("pay_generic", "SUCCESS", 42.0));
        idempotencyProvider.markCompleted(key, acquired.lockOwnerToken(), original, RETENTION_TTL);

        IdempotencyResult<List<PaymentResponse>> cached =
                idempotencyProvider.process(key, LOCK_TTL, responseType.getType());

        assertEquals(ExecutionState.COMPLETED, cached.state());
        assertEquals(PaymentResponse.class, cached.cachedResponse().orElseThrow().get(0).getClass());
        assertEquals(original, cached.cachedResponse().orElseThrow());
    }

    @Test
    @DisplayName("Completed void executions do not deserialize a cached payload")
    void shouldSkipDeserializationForVoidReturnType() {
        Serializer noDeserializationSerializer = new Serializer() {
            @Override
            public <T> String serialize(T object) {
                return "completed";
            }

            @Override
            public <T> T deserialize(String json, Type targetType) {
                throw new AssertionError("Void responses must not be deserialized");
            }
        };
        RedisIdempotencyProvider provider = new RedisIdempotencyProvider(
                atomicStore,
                noDeserializationSerializer,
                KEY_PREFIX
        );
        String key = "idempotency:req_void";
        IdempotencyResult<Void> acquired = provider.process(key, LOCK_TTL, Void.class);
        provider.markCompleted(key, acquired.lockOwnerToken(), null, RETENTION_TTL);

        IdempotencyResult<Void> cached = provider.process(key, LOCK_TTL, Void.class);

        assertEquals(ExecutionState.COMPLETED, cached.state());
        assertTrue(cached.cachedResponse().isEmpty());
    }

    @Test
    @DisplayName("Stale lock owner must not overwrite a newer lock")
    void shouldNotCacheResponseWhenLockOwnershipWasLost() {
        String key = "idempotency:req_107";

        IdempotencyResult<PaymentResponse> staleOwner =
                idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        idempotencyProvider.releaseLock(key, staleOwner.lockOwnerToken());

        IdempotencyResult<PaymentResponse> currentOwner =
                idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.ACQUIRED, currentOwner.state());

        assertThrows(IllegalStateException.class, () -> idempotencyProvider.markCompleted(
                key,
                staleOwner.lockOwnerToken(),
                new PaymentResponse("stale", "SUCCESS", 10.0),
                RETENTION_TTL));

        assertEquals("LOCKED:" + currentOwner.lockOwnerToken(),
                atomicStore.get(KEY_PREFIX + key).orElseThrow());
        assertEquals(ExecutionState.IN_PROGRESS,
                idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class).state());
    }

    @Test
    @DisplayName("Releasing lock on failure should allow immediate retry")
    void shouldAllowRetryAfterLockRelease() {
        String key = "idempotency:req_104";

        // 1. Acquire lock
        IdempotencyResult<PaymentResponse> firstResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.ACQUIRED, firstResult.state());

        // 2. Execution fails -> release lock
        idempotencyProvider.releaseLock(key, firstResult.lockOwnerToken());

        // 3. Retry request should be able to acquire lock again
        IdempotencyResult<PaymentResponse> retryResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.ACQUIRED, retryResult.state());
        assertNotEquals(firstResult.lockOwnerToken(), retryResult.lockOwnerToken());
    }

    @Test
    @DisplayName("Releasing lock with wrong token should not delete active lock")
    void shouldNotReleaseLockWithWrongToken() {
        String key = "idempotency:req_105";

        // 1. Acquire lock
        IdempotencyResult<PaymentResponse> firstResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);

        // 2. Attempt release with invalid token
        idempotencyProvider.releaseLock(key, "invalid-owner-token");

        // 3. Lock should still be held
        IdempotencyResult<PaymentResponse> secondResult = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);
        assertEquals(ExecutionState.IN_PROGRESS, secondResult.state());
    }

    @Test
    @DisplayName("Null keyPrefix should default gracefully without throwing NullPointerException")
    void shouldHandleNullKeyPrefixGracefully() {
        RedisIdempotencyProvider nullPrefixProvider = new RedisIdempotencyProvider(
                atomicStore,
                new JacksonSerializer(),
                null
        );

        String key = "req_106";
        IdempotencyResult<PaymentResponse> result = nullPrefixProvider.process(key, LOCK_TTL, PaymentResponse.class);

        assertEquals(ExecutionState.ACQUIRED, result.state());
        assertTrue(atomicStore.get(key).isPresent());
    }
}