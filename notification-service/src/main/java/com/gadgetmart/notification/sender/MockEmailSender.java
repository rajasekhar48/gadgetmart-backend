package com.gadgetmart.notification.sender;

import com.gadgetmart.notification.config.NotificationProperties;
import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Pretend email provider: prints the email to the log. Can be told to fail randomly to exercise retries. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockEmailSender implements ChannelSender {

    private final NotificationProperties props;

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(Notification n) {
        if (ThreadLocalRandom.current().nextDouble() < props.mock().failureRate()) {
            throw new IllegalStateException("Simulated email provider outage");
        }
        log.info("\n[MOCK EMAIL] to: {}\nsubject: {}\n---\n{}\n---", n.getRecipient(), n.getSubject(), n.getBody());
    }
}
