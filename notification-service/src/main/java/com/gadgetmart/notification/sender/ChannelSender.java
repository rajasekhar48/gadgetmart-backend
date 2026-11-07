package com.gadgetmart.notification.sender;

import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;

/**
 * One implementation per delivery channel. To go live, add e.g. an SmtpEmailSender (JavaMailSender / SendGrid)
 * or a Twilio / MSG91 SmsSender and drop the matching mock. Throwing means "failed, retry later".
 */
public interface ChannelSender {

    Channel channel();

    void send(Notification notification) throws Exception;
}
