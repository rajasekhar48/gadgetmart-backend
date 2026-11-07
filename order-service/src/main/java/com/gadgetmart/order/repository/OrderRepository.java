package com.gadgetmart.order.repository;

import com.gadgetmart.order.domain.CustomerOrder;
import com.gadgetmart.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Scoped by user: someone else's order id simply looks like "not found". */
    Optional<CustomerOrder> findByIdAndUserId(UUID id, UUID userId);

    Page<CustomerOrder> findByUserId(UUID userId, Pageable pageable);

    /** SELECT ... FOR UPDATE: serialises status changes of one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> lockById(@Param("id") UUID id);

    @Query("select o.id from CustomerOrder o where o.status = :status and o.createdAt < :cutoff order by o.createdAt")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") OrderStatus status,
                                            @Param("cutoff") Instant cutoff,
                                            Pageable pageable);
}
