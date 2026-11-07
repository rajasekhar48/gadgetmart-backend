package com.gadgetmart.catalog.repository;

import com.gadgetmart.catalog.domain.Reservation;
import com.gadgetmart.catalog.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);

    /** SELECT ... FOR UPDATE: serialises commit / release / expiry for the same reservation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.orderId = :orderId")
    Optional<Reservation> lockByOrderId(@Param("orderId") UUID orderId);

    /** FOR UPDATE SKIP LOCKED (-2): several replicas can run the expiry job without blocking each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select r from Reservation r where r.status = :status and r.expiresAt < :now order by r.expiresAt")
    List<Reservation> findExpired(@Param("status") ReservationStatus status,
                                  @Param("now") Instant now,
                                  Pageable pageable);
}
