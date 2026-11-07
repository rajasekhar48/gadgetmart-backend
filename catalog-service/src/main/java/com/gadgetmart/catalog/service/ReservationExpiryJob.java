package com.gadgetmart.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net: if payment never completes and no cancel event arrives, stock still comes back.
 * A lost message can therefore never lock a listing forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.reservation.scan-interval-ms:30000}")
    public void releaseExpiredReservations() {
        int released = reservationService.expireDue();
        if (released > 0) {
            log.info("Released {} expired reservation(s)", released);
        }
    }
}
