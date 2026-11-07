package com.gadgetmart.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        int maxAttempts,
        List<Duration> retryBackoff,
        Duration lease,
        int batchSize,
        Mock mock
) {
    public record Mock(double failureRate) {
    }

    /** Wait before the next attempt, given how many attempts have already failed. */
    public Duration backoffAfter(int failedAttempts) {
        int index = Math.min(Math.max(failedAttempts - 1, 0), retryBackoff.size() - 1);
        return retryBackoff.get(index);
    }
}
