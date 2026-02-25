package com.banking.platform.shared.webhook;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * MongoDB repository for outbox/dead-letter webhook events.
 */
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    /** Find all un-replayed dead-letter events for a subscriber endpoint. */
    List<OutboxEvent> findAllByEndpointUrlAndReplayedFalse(String endpointUrl);

    /** Find all un-replayed events for a given owner. */
    List<OutboxEvent> findAllByOwnerIdAndReplayedFalse(java.util.UUID ownerId);
}

