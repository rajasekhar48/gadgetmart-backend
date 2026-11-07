package com.gadgetmart.notification.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** The message format every producer publishes (see OutboxPublisher in Order / User service). */
public record EventEnvelope(
        UUID eventId,            // unique per event: used for idempotent processing
        String eventType,        // UserRegistered | OrderPaid | OrderCancelled
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        JsonNode payload
) {
}
