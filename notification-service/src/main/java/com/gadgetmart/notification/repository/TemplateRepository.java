package com.gadgetmart.notification.repository;

import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByCodeAndChannel(String code, Channel channel);
}
