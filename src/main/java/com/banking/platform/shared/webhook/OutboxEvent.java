package com.banking.platform.shared.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern event document for failed webhook deliveries.
 * Persisted to MongoDB dead-letter collection after all retries exhausted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private String id;

    private UUID ownerId;
    private String eventId;
    private String eventType;
    private String subject;
    private String payload;

    private String endpointUrl;
    private int lastHttpStatus;
    private String lastError;
    private int attempts;

    private Instant createdAt;
    private Instant lastAttemptAt;
    private boolean replayed;
}
