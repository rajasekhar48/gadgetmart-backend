package com.gadgetmart.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String refType,      // ORDER -> the UI links to /orders/{refId}
        UUID refId,
        boolean read,
        Instant createdAt
) {
}
