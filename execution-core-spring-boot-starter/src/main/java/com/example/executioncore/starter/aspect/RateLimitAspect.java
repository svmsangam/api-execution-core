package com.example.executioncore.starter.aspect;

import com.example.executioncore.domain.ratelimit.RateLimitResult;
import com.example.executioncore.domain.ratelimit.RateLimiter;
import com.example.executioncore.starter.annotation.RateLimit;
import com.example.executioncore.starter.exception.RateLimitExceededException;
import com.example.executioncore.starter.expression.SpelExpressionEvaluator;
import com.example.executioncore.starter.properties.ExecutionCoreProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Aspect
public class RateLimitAspect {
    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiter rateLimiter;
    private final SpelExpressionEvaluator expressionEvaluator;
    private final ExecutionCoreProperties.RateLimiterProperties properties;

    public RateLimitAspect(
            RateLimiter rateLimiter,
            SpelExpressionEvaluator expressionEvaluator,
            ExecutionCoreProperties properties
    ) {
        this.rateLimiter = rateLimiter;
        this.expressionEvaluator = expressionEvaluator;
        this.properties = properties.getRateLimiter();
    }

    @Around(value = "@annotation(rateLimit)", argNames = "joinPoint,rateLimit")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = expressionEvaluator.evaluate(joinPoint, rateLimit.key());
        ExecutionCoreProperties.RateLimiterProperties.SlidingWindowProperties defaults =
                properties.getSlidingWindow();
        long capacity = rateLimit.capacity() > 0
                ? rateLimit.capacity()
                : defaults.getDefaultCapacity();
        Duration window = rateLimit.windowSeconds() > 0
                ? Duration.ofSeconds(rateLimit.windowSeconds())
                : defaults.getDefaultWindow();

        if (capacity <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("Configured rate limit capacity and window must be positive");
        }

        log.debug("Evaluating rate limit for key='{}' (capacity={}, window={}s)",
                key, capacity, window.getSeconds());
        RateLimitResult result = rateLimiter.evaluate(
                key,
                capacity,
                window
        );
        if (!result.isAllowed()) {
            log.warn("Rate limit EXCEEDED for key='{}' (capacity={}, window={}s). Throttling request.",
                    key, capacity, window.getSeconds());
            throw new RateLimitExceededException(rateLimit.fallbackMessage(), key);
        }
        log.trace("Rate limit PASSED for key='{}'", key);
        return joinPoint.proceed();
    }
}
