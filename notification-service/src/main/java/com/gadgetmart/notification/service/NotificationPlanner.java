package com.gadgetmart.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;
import com.gadgetmart.notification.domain.NotificationStatus;
import com.gadgetmart.notification.event.EventEnvelope;
import com.gadgetmart.notification.repository.NotificationRepository;
import com.gadgetmart.notification.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Decides WHO gets WHAT for each event and stores the rendered messages.
 * It does not send anything: email / SMS rows are left PENDING for the dispatcher, so a slow or failing
 * provider can never block event consumption. In-app messages are ready immediately.
 *
 * Idempotent: the event id is claimed in the same transaction as the notifications. A redelivered event is
 * skipped, and the unique (source_event_id, channel) constraint is a second safety net.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPlanner {

    private final ProcessedEventRepository processed;
    private final NotificationRepository notifications;
    private final TemplateRenderer renderer;

    @Transactional
    public void handle(EventEnvelope event) {
        if (processed.claim(event.eventId(), Instant.now()) == 0) {
            log.info("Event {} already processed, skipping", event.eventId());
            return;
        }
        JsonNode p = event.payload();
        switch (event.eventType()) {
            case "UserRegistered" -> welcome(event, p);
            case "OrderPaid" -> orderPaid(event, p);
            case "OrderCancelled" -> orderCancelled(event, p);
            default -> log.info("Ignoring event type {}", event.eventType());   // other services' events
        }
    }

    // ------------------------------------------------------------------ per event type

    private void welcome(EventEnvelope event, JsonNode p) {
        UUID userId = uuid(p, "userId");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", firstName(text(p, "fullName")));

        plan(event, "WELCOME", Channel.EMAIL, userId, text(p, "email"), vars, null, null);
        plan(event, "WELCOME", Channel.IN_APP, userId, null, vars, null, null);
    }

    private void orderPaid(EventEnvelope event, JsonNode p) {
        UUID userId = uuid(p, "userId");
        UUID orderId = uuid(p, "orderId");
        Map<String, String> vars = orderVars(p);

        plan(event, "ORDER_PAID", Channel.EMAIL, userId, text(p, "email"), vars, "ORDER", orderId);
        plan(event, "ORDER_PAID", Channel.SMS, userId, text(p, "phone"), vars, "ORDER", orderId);
        plan(event, "ORDER_PAID", Channel.IN_APP, userId, null, vars, "ORDER", orderId);
    }

    private void orderCancelled(EventEnvelope event, JsonNode p) {
        UUID userId = uuid(p, "userId");
        UUID orderId = uuid(p, "orderId");
        Map<String, String> vars = orderVars(p);
        vars.put("reason", StringUtils.hasText(text(p, "reason")) ? text(p, "reason") : "The order could not be completed");

        plan(event, "ORDER_CANCELLED", Channel.EMAIL, userId, text(p, "email"), vars, "ORDER", orderId);
        plan(event, "ORDER_CANCELLED", Channel.IN_APP, userId, null, vars, "ORDER", orderId);
    }

    // ------------------------------------------------------------------ helpers

    private void plan(EventEnvelope event, String type, Channel channel, UUID userId, String recipient,
                      Map<String, String> vars, String refType, UUID refId) {
        if (userId == null) {
            log.warn("Event {} has no userId, nothing to notify", event.eventId());
            return;
        }
        // email / SMS need an address; without one we skip just that channel
        if (channel != Channel.IN_APP && !StringUtils.hasText(recipient)) {
            log.info("No {} recipient for event {}, skipping that channel", channel, event.eventId());
            return;
        }
        var rendered = renderer.render(type, channel, vars);
        if (rendered.isEmpty()) {
            log.warn("No template for {} / {}", type, channel);
            return;
        }

        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setChannel(channel);
        n.setRecipient(channel == Channel.IN_APP ? null : recipient);
        n.setSubject(rendered.get().subject());
        n.setBody(rendered.get().body());
        n.setSourceEventId(event.eventId());
        n.setRefType(refType);
        n.setRefId(refId);
        if (channel == Channel.IN_APP) {
            n.setStatus(NotificationStatus.SENT);          // nothing to deliver: it is simply stored for the inbox
            n.setSentAt(Instant.now());
        } else {
            n.setStatus(NotificationStatus.PENDING);       // picked up by the dispatcher
            n.setNextAttemptAt(Instant.now());
        }
        notifications.save(n);
    }

    private Map<String, String> orderVars(JsonNode p) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("orderNumber", text(p, "orderNumber"));
        vars.put("name", firstName(text(p, "customerName")));
        vars.put("total", formatInr(p.path("totalPaise").asLong(0)));
        vars.put("city", text(p, "shipToCity"));
        vars.put("items", itemsSummary(p.path("items")));
        return vars;
    }

    private String itemsSummary(JsonNode items) {
        List<String> parts = new ArrayList<>();
        for (JsonNode item : items) {
            parts.add(item.path("quantity").asInt(1) + " x " + item.path("title").asText(""));
        }
        return String.join(", ", parts);
    }

    private String formatInr(long paise) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(paise / 100.0);
    }

    private String firstName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private UUID uuid(JsonNode node, String field) {
        String v = text(node, field);
        return v.isEmpty() ? null : UUID.fromString(v);
    }
}
