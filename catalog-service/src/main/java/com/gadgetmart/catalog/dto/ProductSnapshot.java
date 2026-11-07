package com.gadgetmart.catalog.dto;

import java.util.UUID;

/** Minimal, trusted view of a listing for Cart / Order (price re-validation at checkout). */
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
