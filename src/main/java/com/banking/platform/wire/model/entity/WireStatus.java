package com.banking.platform.wire.model.entity;

public enum WireStatus {
    INITIATED,
    PENDING_APPROVAL,
    APPROVED,
    SUBMITTED,
    IN_TRANSIT,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETURNED
}
