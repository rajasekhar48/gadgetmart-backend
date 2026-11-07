package com.gadgetmart.cart.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable form of a cart row: what we keep in Redis and pass around the service layer. */
public record CartLine(UUID listingId, int quantity, long priceAtAddMinor, Instant addedAt) {

    public static CartLine from(CartItem i) {
        return new CartLine(i.getListingId(), i.getQuantity(), i.getUnitPriceAtAddMinor(), i.getAddedAt());
    }
}
