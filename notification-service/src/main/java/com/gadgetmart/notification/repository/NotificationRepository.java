package com.gadgetmart.notification.repository;

import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(UUID userId, Channel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, Channel channel);

    /** Email / SMS that are due. SKIP LOCKED: several replicas share the queue without double-sending. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select n from Notification n
             where n.status = com.gadgetmart.notification.domain.NotificationStatus.PENDING
               and n.nextAttemptAt <= :now
             order by n.nextAttemptAt
            """)
    List<Notification> findDue(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
             where n.id = :id and n.userId = :userId
               and n.channel = com.gadgetmart.notification.domain.Channel.IN_APP and n.readAt is null
            """)
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
             where n.userId = :userId
               and n.channel = com.gadgetmart.notification.domain.Channel.IN_APP and n.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
