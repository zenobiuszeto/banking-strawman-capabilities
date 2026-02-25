package com.banking.platform.ach.model.event;

import com.banking.platform.ach.model.entity.AchDirection;
import com.banking.platform.ach.model.entity.AchStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when ACH transfer state changes occur.
 * Used for event-driven architecture and audit logging.
 */
@Value
@Builder
public class AchEvent {

    /**
     * Unique event identifier
     */
    private String eventId;

    /**
     * Event type: ach.initiated, ach.submitted, ach.settled, ach.returned
     */
    private String eventType;

    /**
     * Timestamp when the event occurred
     */
    private Instant timestamp;

    /**
     * ID of the ACH transfer that triggered this event
     */
    private UUID achTransferId;

    /**
     * Account ID associated with this event
     */
    private UUID accountId;

    /**
     * Direction of the transfer
     */
    private AchDirection direction;

    /**
     * Transfer amount
     */
    private BigDecimal amount;

    /**
     * Status of the transfer at the time of the event
     */
    private AchStatus status;

}
