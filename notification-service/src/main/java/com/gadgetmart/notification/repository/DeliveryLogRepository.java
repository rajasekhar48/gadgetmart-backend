package com.gadgetmart.notification.repository;

import com.gadgetmart.notification.domain.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {
}
