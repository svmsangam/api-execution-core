package com.example.executioncore.starter.aspect;

import com.example.executioncore.domain.ratelimit.RateLimitResult;
import com.example.executioncore.domain.ratelimit.RateLimiter;
import com.example.executioncore.starter.annotation.RateLimit;
import com.example.executioncore.starter.expression.SpelExpressionEvaluator;
import com.example.executioncore.starter.properties.ExecutionCoreProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitAspectTest {
    static class TestService {
        @RateLimit(key = "#p0", capacity = 7, windowSeconds = 9)
        public String custom(String clientId) {
            return clientId;
        }

        @RateLimit(key = "#p0")
        public String defaults(String clientId) {
            return clientId;
        }

        @RateLimit(key = "")
        public String blankKey(String clientId) {
            return clientId;
        }

        @RateLimit(key = "[")
        public String invalidKey(String clientId) {
            return clientId;
        }
    }

    @Test
    void customAnnotationValuesOverrideConfiguredDefaults() throws Throwable {
        CapturingRateLimiter rateLimiter = new CapturingRateLimiter();
        ExecutionCoreProperties properties = properties(100, Duration.ofSeconds(60));
        RateLimitAspect aspect = new RateLimitAspect(rateLimiter, new SpelExpressionEvaluator(), properties);
        TestService service = new TestService();
        Method method = TestService.class.getMethod("custom", String.class);

        Object response = aspect.enforceRateLimit(
                joinPoint(service, method, "client-1"),
                method.getAnnotation(RateLimit.class)
        );

        assertEquals("client-1", response);
        assertEquals("client-1", rateLimiter.key);
        assertEquals(7, rateLimiter.capacity);
        assertEquals(Duration.ofSeconds(9), rateLimiter.window);
    }

    @Test
    void omittedAnnotationValuesUseConfiguredDefaults() throws Throwable {
        CapturingRateLimiter rateLimiter = new CapturingRateLimiter();
        ExecutionCoreProperties properties = properties(23, Duration.ofSeconds(41));
        RateLimitAspect aspect = new RateLimitAspect(rateLimiter, new SpelExpressionEvaluator(), properties);
        TestService service = new TestService();
        Method method = TestService.class.getMethod("defaults", String.class);

        aspect.enforceRateLimit(
                joinPoint(service, method, "client-2"),
                method.getAnnotation(RateLimit.class)
        );

        assertEquals(23, rateLimiter.capacity);
        assertEquals(Duration.ofSeconds(41), rateLimiter.window);
    }

    @Test
    void blankSpelKeyThrowsIllegalArgumentException() throws Exception {
        assertInvalidKey("blankKey");
    }

    @Test
    void malformedSpelKeyThrowsIllegalArgumentException() throws Exception {
        assertInvalidKey("invalidKey");
    }

    private void assertInvalidKey(String methodName) throws Exception {
        CapturingRateLimiter rateLimiter = new CapturingRateLimiter();
        RateLimitAspect aspect = new RateLimitAspect(
                rateLimiter,
                new SpelExpressionEvaluator(),
                properties(10, Duration.ofSeconds(10))
        );
        TestService service = new TestService();
        Method method = TestService.class.getMethod(methodName, String.class);

        assertThrows(IllegalArgumentException.class, () -> aspect.enforceRateLimit(
                joinPoint(service, method, "client"),
                method.getAnnotation(RateLimit.class)
        ));
    }

    private ExecutionCoreProperties properties(long capacity, Duration window) {
        ExecutionCoreProperties properties = new ExecutionCoreProperties();
        properties.getRateLimiter().getSlidingWindow().setDefaultCapacity(capacity);
        properties.getRateLimiter().getSlidingWindow().setDefaultWindow(window);
        return properties;
    }

    private ProceedingJoinPoint joinPoint(Object target, Method method, Object... arguments) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[]{MethodSignature.class},
                (proxy, calledMethod, args) -> calledMethod.getName().equals("getMethod")
                        ? method
                        : defaultValue(calledMethod.getReturnType())
        );
        InvocationHandler handler = (proxy, calledMethod, args) -> switch (calledMethod.getName()) {
            case "getSignature" -> signature;
            case "getTarget" -> target;
            case "getArgs" -> arguments;
            case "proceed" -> method.invoke(target, arguments);
            default -> defaultValue(calledMethod.getReturnType());
        };
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                handler
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private static class CapturingRateLimiter implements RateLimiter {
        private String key;
        private long capacity;
        private Duration window;

        @Override
        public RateLimitResult evaluate(String key, long capacity, Duration window) {
            this.key = key;
            this.capacity = capacity;
            this.window = window;
            return RateLimitResult.allowed(capacity, capacity, java.time.Instant.now().plus(window));
        }
    }
}
