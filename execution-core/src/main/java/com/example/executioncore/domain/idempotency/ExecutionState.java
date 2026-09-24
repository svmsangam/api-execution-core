package com.example.executioncore.domain.idempotency;

public enum ExecutionState {
    ACQUIRED,     // Lock secured; proceed with host application processing.
    IN_PROGRESS,  // Request is currently being executed by another thread/node.
    COMPLETED     // Request was previously completed; response payload is cached.
}
