package com.gadgetmart.cart.dto;

import java.util.List;
import java.util.UUID;

public record CartItemView(
        UUID listingId,
        String title,
        String thumbnailUrl,
        String sellerName,
        int quantity,
        long unitPricePaise,        // CURRENT price from Catalog
        long lineTotalPaise,
        long priceAtAddPaise,       // price the buyer last saw
        int availableQty,
        boolean purchasable,
        List<ItemIssue> issues
) {
}
