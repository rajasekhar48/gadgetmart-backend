package com.gadgetmart.cart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes carts nobody has touched for app.cart.abandon-after. Safe to run on every replica. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartCleanupJob {

    private final CartStore store;

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeAbandonedCarts() {
        int removed = store.purgeAbandoned();
        if (removed > 0) {
            log.info("Deleted {} abandoned cart(s)", removed);
        }
    }
}
