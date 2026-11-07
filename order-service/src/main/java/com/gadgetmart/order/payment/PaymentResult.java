package com.gadgetmart.order.payment;

public record PaymentResult(boolean success, String reference, String failureReason) {

    public static PaymentResult succeeded(String reference) {
        return new PaymentResult(true, reference, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
