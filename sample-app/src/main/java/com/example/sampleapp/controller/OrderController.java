package com.example.sampleapp.controller;

import com.example.executioncore.starter.annotation.Idempotent;
import com.example.executioncore.starter.annotation.RateLimit;
import com.example.sampleapp.model.OrderRequest;
import com.example.sampleapp.model.OrderResponse;
import com.example.sampleapp.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @RateLimit(
            key = "'order:ratelimit:' + #request.customerId",
            capacity = 5,
            windowSeconds = 60,
            fallbackMessage = "Too many order requests. Please try again in a minute."
    )
    @Idempotent(
            key = "'order:idempotency:' + #headers['x-idempotency-key']",
            lockTtlSeconds = 10,
            retentionTtlSeconds = 3600
    )
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader java.util.Map<String, String> headers,
            @RequestBody OrderRequest request) {

        OrderResponse response = orderService.processOrder(request, clientId);
        return ResponseEntity.ok(response);
    }
}
