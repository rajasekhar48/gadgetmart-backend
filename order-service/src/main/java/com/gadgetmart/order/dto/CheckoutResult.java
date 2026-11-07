package com.gadgetmart.order.dto;

/** created=false means this was a replay of an earlier request with the same Idempotency-Key. */
public record CheckoutResult(OrderResponse order, boolean created) {
}
