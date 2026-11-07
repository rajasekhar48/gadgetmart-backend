package com.gadgetmart.notification.service;

import com.gadgetmart.notification.domain.Channel;
import com.gadgetmart.notification.domain.Notification;
import com.gadgetmart.notification.dto.NotificationResponse;
import com.gadgetmart.notification.dto.PageResponse;
import com.gadgetmart.notification.dto.UnreadCount;
import com.gadgetmart.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** The buyer's in-app inbox. Everything is scoped to the caller's own user id. */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.from(notifications
                .findByUserIdAndChannelOrderByCreatedAtDesc(userId, Channel.IN_APP, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadCount unread(UUID userId) {
        return new UnreadCount(notifications.countByUserIdAndChannelAndReadAtIsNull(userId, Channel.IN_APP));
    }

    /** Idempotent: marking an already-read (or someone else's) notification changes nothing. */
    @Transactional
    public void markRead(UUID userId, UUID id) {
        notifications.markRead(id, userId, Instant.now());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, Instant.now());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getSubject(), n.getBody(),
                n.getRefType(), n.getRefId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
