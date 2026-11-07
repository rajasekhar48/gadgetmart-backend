package com.gadgetmart.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderSummary(UUID id, String orderNumber, String status, long totalPaise, String currency,
                           int itemCount, String firstItemTitle, String firstItemThumbnail, Instant createdAt) {
}
