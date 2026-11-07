package com.gadgetmart.order.payment;

import java.util.UUID;

/**
 * @param idempotencyKey stable per order, so a retried charge can never bill twice
 * @param simulation     DEV ONLY hint for the mock provider ("FAIL"); real providers ignore it
 */
public record PaymentRequest(UUID orderId, UUID userId, long amountMinor, String currency,
                             String idempotencyKey, String simulation) {
}
