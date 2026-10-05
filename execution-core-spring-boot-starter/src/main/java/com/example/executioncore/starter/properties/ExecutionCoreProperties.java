package com.example.executioncore.starter.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Root configuration properties for the Execution Core library.
 * <p>
 * Bound to the {@code execution.core} namespace in {@code application.yml}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "execution.core")
public class ExecutionCoreProperties {

    /**
     * Globally enables or disables execution core auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Configured storage adapter type. Options: AUTO, LETTUCE, JEDIS, SPRING_DATA.
     */
    private StorageType storageType = StorageType.AUTO;

    /**
     * Rate limiter module configuration properties.
     */
    private RateLimiterProperties rateLimiter = new RateLimiterProperties();

    /**
     * Idempotency module configuration properties.
     */
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    public enum StorageType {
        AUTO,
        LETTUCE,
        JEDIS,
        SPRING_DATA
    }

    @Getter
    @Setter
    public static class RateLimiterProperties {

        /**
         * Enables or disables rate limiter auto-configuration.
         */
        private boolean enabled = true;

        /**
         * Selected rate limiting algorithm implementation.
         */
        private RateLimiterAlgorithm algorithm = RateLimiterAlgorithm.SLIDING_WINDOW;

        /**
         * Sliding window algorithm specific properties.
         */
        private SlidingWindowProperties slidingWindow = new SlidingWindowProperties();

        @Getter
        @Setter
        public static class SlidingWindowProperties {
            /**
             * Default request capacity permitted per evaluation window.
             */
            private long defaultCapacity = 100;

            /**
             * Default evaluation sliding window duration.
             */
            private Duration defaultWindow = Duration.ofSeconds(60);
        }
    }

    @Getter
    @Setter
    public static class IdempotencyProperties {

        /**
         * Enables or disables idempotency auto-configuration.
         */
        private boolean enabled = true;

        /**
         * Default key prefix prepended to idempotency keys in Redis.
         */
        private String keyPrefix = "idempotency:";

        /**
         * Default lock TTL acquired during active execution.
         */
        private Duration defaultLockTtl = Duration.ofSeconds(30);

        /**
         * Default cache retention duration for execution responses.
         */
        private Duration defaultRetentionTtl = Duration.ofHours(24);
    }
}