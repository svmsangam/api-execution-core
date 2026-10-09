package com.example.executioncore.starter.aspect;

import com.example.executioncore.domain.idempotency.ExecutionState;
import com.example.executioncore.domain.idempotency.IdempotencyProvider;
import com.example.executioncore.domain.idempotency.IdempotencyResult;
import com.example.executioncore.starter.annotation.Idempotent;
import com.example.executioncore.starter.exception.IdempotencyInProgressException;
import com.example.executioncore.starter.expression.SpelExpressionEvaluator;
import com.example.executioncore.starter.properties.ExecutionCoreProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;

@Aspect
public class IdempotencyAspect {
    private final IdempotencyProvider idempotencyProvider;
    private final SpelExpressionEvaluator expressionEvaluator;
    private final ExecutionCoreProperties.IdempotencyProperties properties;

    public IdempotencyAspect(
            IdempotencyProvider idempotencyProvider,
            SpelExpressionEvaluator expressionEvaluator,
            ExecutionCoreProperties properties
    ) {
        this.idempotencyProvider = idempotencyProvider;
        this.expressionEvaluator = expressionEvaluator;
        this.properties = properties.getIdempotency();
    }

    @Around("@annotation(com.example.executioncore.starter.annotation.Idempotent)")
    public Object processIdempotently(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method targetMethod = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, Idempotent.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        }
        if (annotation == null) {
            throw new IllegalStateException("Idempotency advice matched a method without @Idempotent");
        }

        String key = expressionEvaluator.evaluate(joinPoint, annotation.key());
        Duration lockTtl = resolveTtl(annotation.lockTtlSeconds(), properties.getDefaultLockTtl(), "lockTtlSeconds");
        Duration retentionTtl = resolveTtl(
                annotation.retentionTtlSeconds(),
                properties.getDefaultRetentionTtl(),
                "retentionTtlSeconds"
        );

        // 1. Resolve return type and handle ResponseEntity<T> generic unwrapping
        Type genericReturnType = targetMethod.getGenericReturnType();
        boolean isResponseEntity = false;
        Type actualPayloadType = genericReturnType;

        if (genericReturnType instanceof ParameterizedType parameterizedType) {
            if (ResponseEntity.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                isResponseEntity = true;
                actualPayloadType = parameterizedType.getActualTypeArguments()[0];
            }
        } else if (genericReturnType instanceof Class<?> clazz && ResponseEntity.class.isAssignableFrom(clazz)) {
            isResponseEntity = true;
            actualPayloadType = Object.class; // Fallback for raw ResponseEntity without generics
        }

        if (targetMethod.getReturnType() == void.class || targetMethod.getReturnType() == Void.class) {
            actualPayloadType = Void.class;
        }

        // 2. Process idempotency check against actual payload type
        IdempotencyResult<?> result = idempotencyProvider.process(key, lockTtl, actualPayloadType);

        if (result.state() == ExecutionState.COMPLETED) {
            Object cachedBody = result.cachedResponse().orElse(null);
            if (cachedBody == null) {
                return isResponseEntity ? ResponseEntity.ok().build() : null;
            }
            // Re-wrap in ResponseEntity if target method expects ResponseEntity<T>
            return isResponseEntity ? ResponseEntity.ok(cachedBody) : cachedBody;
        }

        if (result.state() == ExecutionState.IN_PROGRESS) {
            throw new IdempotencyInProgressException(key);
        }

        // 3. Execute target method and handle completion/failure lifecycle
        try {
            Object response = joinPoint.proceed();
            Object payloadToCache = response;

            // Unwrap ResponseEntity body before saving to Redis
            if (isResponseEntity && response instanceof ResponseEntity<?> responseEntity) {
                payloadToCache = responseEntity.getBody();
            }

            idempotencyProvider.markCompleted(key, result.lockOwnerToken(), payloadToCache, retentionTtl);
            return response;
        } catch (Throwable failure) {
            try {
                idempotencyProvider.releaseLock(key, result.lockOwnerToken());
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    private Duration resolveTtl(long annotationSeconds, Duration configuredDefault, String attributeName) {
        Duration ttl;
        if (annotationSeconds == -1) {
            ttl = configuredDefault;
        } else if (annotationSeconds > 0) {
            ttl = Duration.ofSeconds(annotationSeconds);
        } else {
            throw new IllegalArgumentException("@Idempotent " + attributeName + " must be positive or -1");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("Configured idempotency " + attributeName + " must be positive");
        }
        return ttl;
    }
}
