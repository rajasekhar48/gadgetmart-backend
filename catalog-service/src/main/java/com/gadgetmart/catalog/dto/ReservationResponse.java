package com.gadgetmart.catalog.dto;

import com.gadgetmart.catalog.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(UUID orderId, ReservationStatus status, Instant expiresAt, List<Item> items) {
    public record Item(UUID listingId, int quantity) {
    }
}
