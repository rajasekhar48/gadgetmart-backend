package com.gadgetmart.notification.sender;

import com.gadgetmart.notification.config.NotificationProperties;
import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Pretend SMS gateway: prints the message to the log. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockSmsSender implements ChannelSender {

    private final NotificationProperties props;

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public void send(Notification n) {
        if (ThreadLocalRandom.current().nextDouble() < props.mock().failureRate()) {
            throw new IllegalStateException("Simulated SMS gateway outage");
        }
        log.info("[MOCK SMS] to: {} | {}", n.getRecipient(), n.getBody());
    }
}
