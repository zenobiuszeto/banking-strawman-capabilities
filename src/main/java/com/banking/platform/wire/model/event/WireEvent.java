package com.banking.platform.wire.model.event;

import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WireEvent {
    private UUID eventId;
    private String eventType; // wire.initiated, wire.approved, wire.completed, wire.failed
    private Instant timestamp;
    private UUID wireTransferId;
    private UUID accountId;
    private WireType wireType;
    private BigDecimal amount;
    private String currency;
    private WireStatus status;
}
