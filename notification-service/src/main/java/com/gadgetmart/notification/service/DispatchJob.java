package com.gadgetmart.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchJob {

    private final DeliveryService delivery;

    @Scheduled(fixedDelayString = "${app.notification.poll-interval-ms:2000}")
    public void dispatch() {
        try {
            delivery.dispatchDue();
        } catch (Exception e) {
            log.warn("Dispatch run failed: {}", e.toString());
        }
    }
}
