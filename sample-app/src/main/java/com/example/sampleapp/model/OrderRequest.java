package com.example.sampleapp.model;

import lombok.Data;

@Data
public class OrderRequest {
    private String orderId;
    private String customerId;
    private double amount;
}