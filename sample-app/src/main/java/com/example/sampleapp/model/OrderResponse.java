package com.example.sampleapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String status;
    private String transactionId;
    private String processedAt;
}
