package com.example.sampleapp.controller;

import com.example.executioncore.domain.idempotency.ExecutionState;
import com.example.executioncore.domain.idempotency.IdempotencyProvider;
import com.example.executioncore.domain.idempotency.IdempotencyResult;
import com.example.executioncore.domain.ratelimit.RateLimitResult;
import com.example.executioncore.domain.ratelimit.RateLimiter;
import com.example.sampleapp.model.OrderRequest;
import com.example.sampleapp.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final RateLimiter rateLimiter;
    private final IdempotencyProvider idempotencyProvider;

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody OrderRequest request
    ) {
        // 1. Evaluate Rate Limiting (5 requests per 10s per client)
        RateLimitResult rateCheck = rateLimiter.evaluate(clientId, 5, Duration.ofSeconds(10));
        if (!rateCheck.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", String.valueOf(rateCheck.remainingTokens()))
                    .body("Rate limit exceeded. Try again later.");
        }

        // 2. Evaluate Idempotency
        IdempotencyResult<OrderResponse> result = idempotencyProvider.process(
                idempotencyKey,
                Duration.ofSeconds(15),
                OrderResponse.class
        );

        // Handle the three explicit execution states
        if (result.state() == ExecutionState.COMPLETED) {
            return result.cachedResponse()
                    .map(response -> ResponseEntity.ok().header("X-Cache-Hit", "true").body(response))
                    .orElseGet(() -> ResponseEntity.ok().build());
        }

        if (result.state() == ExecutionState.IN_PROGRESS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Request with this idempotency key is currently being processed.");
        }

        // 3. State is ExecutionState.ACQUIRED - Execute Business Logic
        try {
            OrderResponse response = new OrderResponse(
                    request.getOrderId(),
                    "SUCCESS",
                    "TXN-" + System.currentTimeMillis(),
                    java.time.Instant.now().toString()
            );

            // Commit execution response and release lock
            idempotencyProvider.markCompleted(
                    idempotencyKey,
                    result.lockOwnerToken(),
                    response,
                    Duration.ofMinutes(5)
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Release lock on failure so caller can retry safely
            idempotencyProvider.releaseLock(idempotencyKey, result.lockOwnerToken());
            throw e;
        }
    }
}
