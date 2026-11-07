package com.gadgetmart.cart.repository;

import com.gadgetmart.cart.domain.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    /** Race-free "create if missing": two simultaneous first requests both succeed. */
    @Modifying
    @Query(value = "INSERT INTO carts (user_id, created_at, updated_at) VALUES (:userId, :now, :now) "
            + "ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    int ensureExists(@Param("userId") UUID userId, @Param("now") Instant now);

    /** SELECT ... FOR UPDATE: serialises edits to one user's cart (keeps the line limit exact). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> lockByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("update Cart c set c.updatedAt = :now where c.userId = :userId")
    int touch(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Items go with it through ON DELETE CASCADE. */
    @Modifying
    @Query("delete from Cart c where c.updatedAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
