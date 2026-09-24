

import com.example.executioncore.domain.ratelimit.RateLimitResult;
import com.example.executioncore.domain.ratelimit.SlidingWindowRateLimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowRateLimiterTest {

    private InMemoryAtomicStore atomicStore;
    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        atomicStore = new InMemoryAtomicStore();
        rateLimiter = new SlidingWindowRateLimiter(atomicStore);
    }

    @Test
    @DisplayName("Should allow requests within configured capacity limit")
    void shouldAllowRequestsWithinCapacity() {
        String clientKey = "user_123";
        long capacity = 3;
        Duration window = Duration.ofSeconds(10);

        RateLimitResult res1 = rateLimiter.evaluate(clientKey, capacity, window);
        assertTrue(res1.isAllowed());
        assertEquals(2, res1.remainingTokens());

        RateLimitResult res2 = rateLimiter.evaluate(clientKey, capacity, window);
        assertTrue(res2.isAllowed());
        assertEquals(1, res2.remainingTokens());

        RateLimitResult res3 = rateLimiter.evaluate(clientKey, capacity, window);
        assertTrue(res3.isAllowed());
        assertEquals(0, res3.remainingTokens());
    }

    @Test
    @DisplayName("Should deny request when capacity is exceeded")
    void shouldDenyRequestWhenCapacityExceeded() {
        String clientKey = "user_456";
        long capacity = 2;
        Duration window = Duration.ofSeconds(10);

        // Consume all capacity
        rateLimiter.evaluate(clientKey, capacity, window);
        rateLimiter.evaluate(clientKey, capacity, window);

        // Third request should be denied
        RateLimitResult overflowResult = rateLimiter.evaluate(clientKey, capacity, window);
        assertFalse(overflowResult.isAllowed());
        assertEquals(0, overflowResult.remainingTokens());
    }

    @Test
    @DisplayName("Should isolate rate limit buckets by key")
    void shouldIsolateLimitsByKey() {
        long capacity = 1;
        Duration window = Duration.ofSeconds(10);

        RateLimitResult userAResult = rateLimiter.evaluate("user_A", capacity, window);
        assertTrue(userAResult.isAllowed());

        // User A is now blocked
        assertFalse(rateLimiter.evaluate("user_A", capacity, window).isAllowed());

        // User B should still be allowed
        RateLimitResult userBResult = rateLimiter.evaluate("user_B", capacity, window);
        assertTrue(userBResult.isAllowed());
    }

    @Test
    @DisplayName("Should reset capacity after sliding window passes")
    void shouldResetCapacityAfterWindowExpires() throws InterruptedException {
        String clientKey = "user_789";
        long capacity = 1;
        Duration window = Duration.ofMillis(200); // Short window for testing time progression

        // First request consumes capacity
        assertTrue(rateLimiter.evaluate(clientKey, capacity, window).isAllowed());

        // Immediate second request is blocked
        assertFalse(rateLimiter.evaluate(clientKey, capacity, window).isAllowed());

        // Wait for window to slide past
        Thread.sleep(250);

        // Capacity should reset
        RateLimitResult postExpirationResult = rateLimiter.evaluate(clientKey, capacity, window);
        assertTrue(postExpirationResult.isAllowed());
        assertEquals(0, postExpirationResult.remainingTokens());
    }
}