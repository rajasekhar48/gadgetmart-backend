package com.gadgetmart.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadgetmart.order.domain.OutboxEvent;
import com.gadgetmart.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Transactional-outbox relay: publishes rows written by the business transaction (same transaction as the order status change) to Kafka.
 * Delivery is AT LEAST ONCE: if the commit fails after a successful send, the row is sent again, which is why
 * every consumer is idempotent (it remembers processed event ids).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    @Value("${app.kafka.topic}")
    private String topic;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Value("${app.outbox.retain-published}")
    private Duration retainPublished;

    /** Locks a batch (SKIP LOCKED, so replicas share the work), sends in order, and marks each row once Kafka acknowledged it. */
    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = outbox.lockUnpublished(PageRequest.of(0, batchSize));
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                // key = aggregate id: all events of one order / user stay in order on one partition
                kafka.send(topic, event.getAggregateId().toString(), toJson(event)).get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Stop here so ordering is kept; rows already marked in this batch are committed, the rest retry next tick.
                log.warn("Kafka publish failed for outbox event {}: {}. Will retry.", event.getId(), e.toString());
                break;
            }
        }
        return sent;
    }

    @Transactional
    public int purgePublished() {
        return outbox.deletePublishedBefore(Instant.now().minus(retainPublished));
    }

    private String toJson(OutboxEvent event) throws JsonProcessingException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.getId().toString());          // consumers dedupe on this
        envelope.put("eventType", event.getEventType());
        envelope.put("aggregateType", event.getAggregateType());
        envelope.put("aggregateId", event.getAggregateId().toString());
        envelope.put("occurredAt", event.getCreatedAt().toString());
        envelope.put("payload", event.getPayload());
        return mapper.writeValueAsString(envelope);
    }
}
