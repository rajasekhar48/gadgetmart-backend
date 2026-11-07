package com.gadgetmart.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String status,
        long totalPaise,
        String currency,
        List<OrderItemResponse> items,
        AddressResponse shippingAddress,
        PaymentResponse payment,
        List<StatusChangeResponse> timeline,
        String failureReason,
        Instant createdAt
) {
}
