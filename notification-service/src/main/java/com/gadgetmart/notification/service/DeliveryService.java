package com.gadgetmart.notification.service;

import com.gadgetmart.notification.config.NotificationProperties;
import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.DeliveryLog;
import com.gadgetmart.notification.domain.Notification;
import com.gadgetmart.notification.domain.NotificationStatus;
import com.gadgetmart.notification.repository.DeliveryLogRepository;
import com.gadgetmart.notification.repository.NotificationRepository;
import com.gadgetmart.notification.sender.ChannelSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends PENDING email / SMS with retries and exponential backoff.
 *
 * Three short steps, never one long transaction around the provider call:
 *   1. claim:   lock due rows (SKIP LOCKED) and push their next_attempt_at forward by a "lease"
 *   2. send:    call the provider, outside any transaction
 *   3. record:  mark SENT, or schedule the next attempt, or give up (FAILED); write a delivery_log row
 * If the service dies during step 2 the lease runs out and the notification is simply tried again.
 */
@Slf4j
@Service
public class DeliveryService {

    private final NotificationRepository notifications;
    private final DeliveryLogRepository deliveryLog;
    private final NotificationProperties props;
    private final TransactionTemplate tx;
    private final Map<Channel, ChannelSender> senders = new EnumMap<>(Channel.class);

    public DeliveryService(NotificationRepository notifications, DeliveryLogRepository deliveryLog,
                           NotificationProperties props, TransactionTemplate tx, List<ChannelSender> channelSenders) {
        this.notifications = notifications;
        this.deliveryLog = deliveryLog;
        this.props = props;
        this.tx = tx;
        channelSenders.forEach(s -> senders.put(s.channel(), s));
    }

    /** @return how many notifications were attempted */
    public int dispatchDue() {
        List<UUID> claimed = tx.execute(status -> {
            Instant now = Instant.now();
            List<Notification> due = notifications.findDue(now, PageRequest.of(0, props.batchSize()));
            due.forEach(n -> n.setNextAttemptAt(now.plus(props.lease())));      // the lease
            return due.stream().map(Notification::getId).toList();
        });
        if (claimed == null) {
            return 0;
        }
        claimed.forEach(this::deliver);
        return claimed.size();
    }

    private void deliver(UUID id) {
        Notification n = notifications.findById(id).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        Exception failure = null;
        try {
            ChannelSender sender = senders.get(n.getChannel());
            if (sender == null) {
                throw new IllegalStateException("No sender configured for " + n.getChannel());
            }
            sender.send(n);
        } catch (Exception e) {
            failure = e;
        }
        record(id, failure);
    }

    private void record(UUID id, Exception failure) {
        tx.executeWithoutResult(status -> {
            Notification n = notifications.findById(id).orElseThrow();
            int attempt = n.getAttempts() + 1;
            n.setAttempts(attempt);

            if (failure == null) {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(Instant.now());
                n.setNextAttemptAt(null);
                n.setLastError(null);
                deliveryLog.save(new DeliveryLog(id, attempt, "SENT", null));
                return;
            }

            String error = truncate(failure.toString());
            n.setLastError(error);
            if (attempt >= props.maxAttempts()) {
                n.setStatus(NotificationStatus.FAILED);
                n.setNextAttemptAt(null);
                deliveryLog.save(new DeliveryLog(id, attempt, "FAILED", error));
                log.error("Giving up on {} {} to {} after {} attempts: {}",
                        n.getType(), n.getChannel(), n.getRecipient(), attempt, error);
            } else {
                n.setNextAttemptAt(Instant.now().plus(props.backoffAfter(attempt)));
                deliveryLog.save(new DeliveryLog(id, attempt, "RETRY", error));
                log.warn("Attempt {} failed for {} {} to {}; retrying later: {}",
                        attempt, n.getType(), n.getChannel(), n.getRecipient(), error);
            }
        });
    }

    private String truncate(String s) {
        return s.length() <= 480 ? s : s.substring(0, 480);
    }
}
