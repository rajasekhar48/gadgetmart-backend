package com.gadgetmart.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadgetmart.notification.event.EventEnvelope;
import com.gadgetmart.notification.service.NotificationPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Entry point for all domain events. Any exception here is retried by the container's error handler and,
 * if it keeps failing, the message is parked on "<topic>.DLT" (see KafkaConfig).
 * The offset is committed only after this method returns, so an event is never lost; it may be delivered
 * twice, which NotificationPlanner tolerates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final ObjectMapper mapper;
    private final NotificationPlanner planner;

    @KafkaListener(topics = {"${app.kafka.topics.orders}", "${app.kafka.topics.users}"})
    public void onMessage(String message) throws JsonProcessingException {
        EventEnvelope event = mapper.readValue(message, EventEnvelope.class);
        log.info("Received {} ({})", event.eventType(), event.eventId());
        planner.handle(event);
    }
}
