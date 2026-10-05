package com.example.executioncore.starter.properties;

/**
 * Supported rate limiting algorithms for auto-configuration.
 */
public enum RateLimiterAlgorithm {
    SLIDING_WINDOW,
    TOKEN_BUCKET,
    LEAKY_BUCKET
}