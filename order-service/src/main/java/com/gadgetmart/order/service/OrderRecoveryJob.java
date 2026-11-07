package com.gadgetmart.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Finishes or cleans up orders left in PENDING_PAYMENT (service crash, timeout after payment, ...).
 * Every step it performs is idempotent. With several replicas, two may pick the same order; that is
 * harmless for commit / release / cancel, but add a SKIP LOCKED claim before running a real refund path
 * on many replicas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRecoveryJob {

    private static final int BATCH = 50;

    private final OrderStore store;
    private final CheckoutService checkoutService;

    @Value("${app.checkout.recover-after}")
    private Duration recoverAfter;

    @Scheduled(fixedDelayString = "${app.checkout.recovery-interval-ms:60000}")
    public void recoverPendingOrders() {
        List<UUID> ids = store.findPendingOlderThan(Instant.now().minus(recoverAfter), BATCH);
        for (UUID id : ids) {
            try {
                checkoutService.recover(id);
            } catch (Exception e) {
                log.error("Recovery failed for order {}", id, e);
            }
        }
    }
}
