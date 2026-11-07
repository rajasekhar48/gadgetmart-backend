package com.gadgetmart.order.dto;

import java.util.UUID;

public record OrderItemResponse(UUID listingId, String title, String sellerName, String thumbnailUrl,
                                int quantity, long unitPricePaise, long lineTotalPaise) {
}
