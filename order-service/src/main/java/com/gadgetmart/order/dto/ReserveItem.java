package com.gadgetmart.order.dto;

import java.util.UUID;

public record ReserveItem(UUID listingId, int quantity) {
}
