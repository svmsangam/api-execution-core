package com.example.sampleapp.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequest {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
}