package com.example.sampleapp.exception;


import com.example.executioncore.starter.exception.IdempotencyInProgressException;
import com.example.executioncore.starter.exception.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
            "error", "Too Many Requests",
            "message", ex.getMessage(),
            "status", 429
        ));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentExecution(IdempotencyInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "error", "Conflict",
            "message", ex.getMessage(),
            "status", 409
        ));
    }
}