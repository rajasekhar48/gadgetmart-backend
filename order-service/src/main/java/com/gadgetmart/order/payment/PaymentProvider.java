package com.gadgetmart.order.payment;

/**
 * The seam for real payment gateways. To integrate Razorpay / Stripe later, add an implementation
 * (hosted checkout + webhook confirmation) and set app.payment.provider; the order flow does not change.
 */
public interface PaymentProvider {

    /** Stored on the payment row, e.g. "mock", "razorpay". */
    String name();

    PaymentResult charge(PaymentRequest request);

    /** Used when a captured payment must be undone (e.g. the stock hold was lost). */
    void refund(String providerReference, long amountMinor);
}
