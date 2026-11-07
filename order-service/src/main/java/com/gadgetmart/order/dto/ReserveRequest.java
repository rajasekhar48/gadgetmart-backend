package com.gadgetmart.order.dto;

import java.util.List;
import java.util.UUID;

public record ReserveRequest(UUID orderId, List<ReserveItem> items) {
}
