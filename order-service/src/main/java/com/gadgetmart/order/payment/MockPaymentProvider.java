package com.gadgetmart.order.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Pretend gateway: every payment succeeds instantly, unless a dev request asks it to fail. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Value("${app.payment.mock.allow-simulation:true}")
    private boolean allowSimulation;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        if (allowSimulation && "FAIL".equalsIgnoreCase(request.simulation())) {
            log.info("[mock-payment] declining order {} (simulated)", request.orderId());
            return PaymentResult.failed("Payment declined (simulated)");
        }
        String reference = "MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("[mock-payment] captured {} {} for order {} ref {}",
                request.amountMinor(), request.currency(), request.orderId(), reference);
        return PaymentResult.succeeded(reference);
    }

    @Override
    public void refund(String providerReference, long amountMinor) {
        log.info("[mock-payment] refunded {} for ref {}", amountMinor, providerReference);
    }
}
