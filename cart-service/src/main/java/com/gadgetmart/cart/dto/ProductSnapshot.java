package com.gadgetmart.cart.dto;

import java.util.UUID;

/** Mirrors Catalog's /internal/products/batch response. */
public record ProductSnapshot(
        UUID id,
        String title,
        long pricePaise,
        String currency,
        boolean active,
        int availableQty,
        String thumbnailUrl,
        String sellerName
) {
}
