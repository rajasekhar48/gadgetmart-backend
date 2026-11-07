package com.gadgetmart.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    public void relay() {
        try {
            int sent = publisher.publishBatch();
            if (sent > 0) {
                log.debug("Published {} outbox event(s)", sent);
            }
        } catch (Exception e) {
            log.warn("Outbox relay run failed: {}", e.toString());
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purge() {
        int removed = publisher.purgePublished();
        if (removed > 0) {
            log.info("Purged {} published outbox row(s)", removed);
        }
    }
}
