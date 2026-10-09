package com.example.executioncore.starter.aspect;

import com.example.executioncore.domain.idempotency.IdempotencyProvider;
import com.example.executioncore.domain.idempotency.IdempotencyResult;
import com.example.executioncore.starter.annotation.Idempotent;
import com.example.executioncore.starter.expression.SpelExpressionEvaluator;
import com.example.executioncore.starter.properties.ExecutionCoreProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyAspectTest {
    record TestDto(String value) {}

    static class TestService {
        @Idempotent(key = "#p0")
        public List<TestDto> execute(String key) {
            return List.of(new TestDto(key));
        }
    }

    @Test
    void usesGenericReturnTypeAndConfiguredTtlDefaults() throws Throwable {
        TestService service = new TestService();
        Method method = TestService.class.getMethod("execute", String.class);
        CapturingIdempotencyProvider provider = new CapturingIdempotencyProvider();
        ExecutionCoreProperties properties = new ExecutionCoreProperties();
        properties.getIdempotency().setDefaultLockTtl(Duration.ofSeconds(17));
        properties.getIdempotency().setDefaultRetentionTtl(Duration.ofHours(3));
        IdempotencyAspect aspect = new IdempotencyAspect(provider, new SpelExpressionEvaluator(), properties);

        Object result = aspect.processIdempotently(joinPoint(service, method, "order-1"));

        assertEquals(method.getGenericReturnType(), provider.returnType);
        assertEquals(Duration.ofSeconds(17), provider.lockTtl);
        assertEquals(Duration.ofHours(3), provider.retentionTtl);
        assertEquals(List.of(new TestDto("order-1")), result);
    }

    private ProceedingJoinPoint joinPoint(Object target, Method method, Object... arguments) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[]{MethodSignature.class},
                (proxy, calledMethod, args) -> calledMethod.getName().equals("getMethod") ? method : defaultValue(calledMethod.getReturnType())
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

    private static class CapturingIdempotencyProvider implements IdempotencyProvider {
        private Type returnType;
        private Duration lockTtl;
        private Duration retentionTtl;

        @Override
        public <T> IdempotencyResult<T> process(String key, Duration lockTtl, Type returnType) {
            this.returnType = returnType;
            this.lockTtl = lockTtl;
            return IdempotencyResult.acquired("lock-token");
        }

        @Override
        public <T> void markCompleted(String key, String lockOwnerToken, T response, Duration retentionTtl) {
            this.retentionTtl = retentionTtl;
        }

        @Override
        public void releaseLock(String key, String lockOwnerToken) {
            throw new AssertionError("No lock release expected");
        }
    }
}
