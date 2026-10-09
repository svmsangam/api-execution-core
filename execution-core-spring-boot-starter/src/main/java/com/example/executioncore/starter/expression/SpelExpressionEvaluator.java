package com.example.executioncore.starter.expression;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for evaluating SpEL expressions against AOP {@link ProceedingJoinPoint} contexts.
 * <p>
 * Employs {@link MethodBasedEvaluationContext} for parameter binding and caches compiled
 * AST {@link Expression} instances to minimize runtime reflection overhead.
 */
public class SpelExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Evaluates a SpEL expression string against the target method call arguments.
     *
     * @param joinPoint        the active AOP proceeding join point
     * @param expressionString the SpEL expression to evaluate (e.g., "#request.id")
     * @return the evaluated expression result converted to string
     */
    public String evaluate(ProceedingJoinPoint joinPoint, String expressionString) {
        if (!StringUtils.hasText(expressionString)) {
            throw new IllegalArgumentException("SpEL expression must not be blank");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());

        // Spring automatically populates #parameterName, #p0, #a0, and #root.args
        EvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(),
                method,
                joinPoint.getArgs(),
                parameterNameDiscoverer
        );

        // Retrieve cached AST or compile and cache if first access
        Object value;
        try {
            Expression expression = expressionCache.computeIfAbsent(
                    expressionString,
                    parser::parseExpression
            );
            value = expression.getValue(context);
        } catch (ExpressionException exception) {
            throw new IllegalArgumentException("Invalid SpEL expression: " + expressionString, exception);
        }
        if (value == null) {
            throw new IllegalArgumentException("SpEL expression evaluated to null: " + expressionString);
        }

        return value.toString();
    }
}
