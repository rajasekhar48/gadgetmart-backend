package com.gadgetmart.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        /** DEV ONLY: "FAIL" makes the mock payment decline, so the failure path can be tested. */
        @Pattern(regexp = "^(SUCCESS|FAIL)$", message = "must be SUCCESS or FAIL") String simulatePayment
) {
}
