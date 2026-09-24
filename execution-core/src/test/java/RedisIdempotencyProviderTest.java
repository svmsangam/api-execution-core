

import com.example.executioncore.domain.idempotency.ExecutionState;
import com.example.executioncore.domain.idempotency.IdempotencyResult;
import com.example.executioncore.domain.idempotency.RedisIdempotencyProvider;
import com.example.executioncore.domain.serializer.JacksonSerializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RedisIdempotencyProviderTest {

    private InMemoryAtomicStore atomicStore;
    private RedisIdempotencyProvider idempotencyProvider;

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration RETENTION_TTL = Duration.ofHours(1);

    // Dummy DTO for testing serialization
    record PaymentResponse(String paymentId, String status, double amount) {}

    @BeforeEach
    void setUp() {
        atomicStore = new InMemoryAtomicStore();
        idempotencyProvider = new RedisIdempotencyProvider(atomicStore, new JacksonSerializer());
    }

    @Test
    @DisplayName("First request should acquire execution lock successfully")
    void shouldAcquireLockOnFirstRequest() {
        String key = "idempotency:req_101";

        IdempotencyResult<PaymentResponse> result = idempotencyProvider.process(key, LOCK_TTL, PaymentResponse.class);

        assertEquals(ExecutionState.ACQUIRED, result.state());
        assertNotNull(result.lockOwnerToken());
        assertTrue(result.cachedResponse().isEmpty());
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
}