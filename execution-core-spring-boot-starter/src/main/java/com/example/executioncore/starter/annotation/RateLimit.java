package com.example.executioncore.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {
    String key();

    long capacity() default -1L;

    long windowSeconds() default -1L;

    String fallbackMessage() default "Rate limit exceeded. Please try again later.";
}
