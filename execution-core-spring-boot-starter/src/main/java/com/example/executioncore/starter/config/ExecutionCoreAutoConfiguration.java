package com.example.executioncore.starter.config;

import com.example.executioncore.domain.idempotency.IdempotencyProvider;
import com.example.executioncore.domain.idempotency.RedisIdempotencyProvider;
import com.example.executioncore.domain.ratelimit.RateLimiter;
import com.example.executioncore.domain.ratelimit.SlidingWindowRateLimiter;
import com.example.executioncore.domain.serializer.JacksonSerializer;
import com.example.executioncore.domain.serializer.Serializer;
import com.example.executioncore.domain.storage.AtomicStore;
import com.example.executioncore.starter.aspect.IdempotencyAspect;
import com.example.executioncore.starter.aspect.RateLimitAspect;
import com.example.executioncore.starter.expression.SpelExpressionEvaluator;
import com.example.executioncore.starter.properties.ExecutionCoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Main autoconfiguration responsible for registering core domain services:
 * {@link RateLimiter} and {@link IdempotencyProvider}.
 * <p>
 * Evaluated after {@link ExecutionStorageAutoConfiguration} to ensure an {@link AtomicStore}
 * bean is available in the context.
 */
@AutoConfiguration(after = ExecutionStorageAutoConfiguration.class)
@EnableConfigurationProperties(ExecutionCoreProperties.class)
@ConditionalOnProperty(prefix = "execution.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AtomicStore.class)
public class ExecutionCoreAutoConfiguration {

    @Bean
    public SpelExpressionEvaluator spelExpressionEvaluator() {
        return new SpelExpressionEvaluator();
    }

    // =========================================================================
    // 1. Rate Limiter Strategy Provisioning
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "execution.core.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RateLimiterConfiguration {

        /**
         * Registers {@link SlidingWindowRateLimiter} when algorithm is set to SLIDING_WINDOW
         * or left unspecified (default).
         */
        @Bean
        @ConditionalOnMissingBean(RateLimiter.class)
        @ConditionalOnProperty(
                prefix = "execution.core.rate-limiter",
                name = "algorithm",
                havingValue = "SLIDING_WINDOW",
                matchIfMissing = true
        )
        public RateLimiter slidingWindowRateLimiter(AtomicStore atomicStore) {
            return new SlidingWindowRateLimiter(atomicStore);
        }

        @Bean
        public RateLimitAspect rateLimitAspect(
                RateLimiter rateLimiter,
                SpelExpressionEvaluator expressionEvaluator,
                ExecutionCoreProperties properties
        ) {
            return new RateLimitAspect(rateLimiter, expressionEvaluator, properties);
        }
    }

    // =========================================================================
    // 2. Idempotency Provider Provisioning
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "execution.core.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class IdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean(IdempotencyProvider.class)
        public IdempotencyProvider idempotencyProvider(
                AtomicStore atomicStore,
                Serializer serializer,
                ExecutionCoreProperties properties
        ) {
            return new RedisIdempotencyProvider(atomicStore, serializer,properties.getIdempotency().getKeyPrefix());
        }

        @Bean
        public IdempotencyAspect idempotencyAspect(
                IdempotencyProvider idempotencyProvider,
                SpelExpressionEvaluator expressionEvaluator,
                ExecutionCoreProperties properties
        ) {
            return new IdempotencyAspect(idempotencyProvider, expressionEvaluator, properties);
        }
    }


    // =========================================================================
    // 3. Default Serializer Provisioning
    // =========================================================================
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObjectMapper.class)
    static class SerializerConfiguration {

        @Bean
        @ConditionalOnMissingBean(Serializer.class)
        public Serializer defaultSerializer(ObjectMapper objectMapper) {
            return new JacksonSerializer(objectMapper);
        }
    }
}