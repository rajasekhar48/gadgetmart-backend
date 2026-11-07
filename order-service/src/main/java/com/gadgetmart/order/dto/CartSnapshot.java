package com.gadgetmart.order.dto;

import java.util.List;
import java.util.UUID;

/** Mirrors Cart's /internal/carts/{userId} response (only the fields Order needs). */
public record CartSnapshot(List<Line> items, int itemCount, int lineCount, long subtotalPaise,
                           String currency, boolean checkoutReady) {

    public record Line(UUID listingId, String title, String thumbnailUrl, String sellerName,
                       int quantity, long unitPricePaise, long lineTotalPaise,
                       boolean purchasable, List<String> issues) {
    }
}
