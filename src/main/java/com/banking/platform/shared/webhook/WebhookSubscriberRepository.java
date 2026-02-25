package com.banking.platform.shared.webhook;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * MongoDB repository for webhook subscriber registrations.
 */
public interface WebhookSubscriberRepository extends MongoRepository<WebhookSubscriber, String> {

    /**
     * Find all active subscribers interested in the given event type.
     *
     * @param eventType dot-notation event type, e.g. "transaction.created"
     * @return list of active subscribers
     */
    List<WebhookSubscriber> findAllByEventTypesContainingAndActiveTrue(String eventType);

    /**
     * Find all subscriptions for a given owner.
     */
    List<WebhookSubscriber> findAllByOwnerId(java.util.UUID ownerId);
}

