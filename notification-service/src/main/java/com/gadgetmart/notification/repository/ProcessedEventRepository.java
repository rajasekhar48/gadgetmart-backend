package com.gadgetmart.notification.repository;

import com.gadgetmart.notification.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Atomically claims an event id. Returns 1 the first time and 0 for every duplicate, even when two
     * deliveries race, so the caller can simply skip when it gets 0.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:id, :now) ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("now") Instant now);
}
