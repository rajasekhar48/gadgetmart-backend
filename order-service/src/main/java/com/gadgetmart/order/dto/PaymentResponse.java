package com.gadgetmart.order.dto;

public record PaymentResponse(String status, String provider, String reference, String failureReason) {
}
