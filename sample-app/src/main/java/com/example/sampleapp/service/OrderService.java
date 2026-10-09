package com.example.sampleapp.service;

import com.example.sampleapp.model.OrderRequest;
import com.example.sampleapp.model.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * Simulates processing an order request.
     *
     * @param request  the order request details
     * @param clientId the client executing the request
     * @return the processed order response
     */
    public OrderResponse processOrder(OrderRequest request, String clientId) {
        log.info("Processing order '{}' for customer '{}' (Client: {})", 
                request.getOrderId(), request.getCustomerId(), clientId);

        // Simulate business processing overhead (e.g., DB operations or payment gateway calls)
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Order processing interrupted", e);
        }

        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Order '{}' processed successfully. Generated transactionId='{}'", 
                request.getOrderId(), transactionId);

        return new OrderResponse(
                request.getOrderId(),
                request.getCustomerId(),
                request.getAmount(),
                "SUCCESS",
                transactionId,
                Instant.now().toString()
        );
    }
}