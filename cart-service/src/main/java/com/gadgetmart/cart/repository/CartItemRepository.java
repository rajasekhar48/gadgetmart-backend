package com.gadgetmart.cart.repository;

import com.gadgetmart.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findAllByUserIdOrderByAddedAtAsc(UUID userId);

    Optional<CartItem> findByUserIdAndListingId(UUID userId, UUID listingId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("delete from CartItem i where i.userId = :userId and i.listingId = :listingId")
    int deleteLine(@Param("userId") UUID userId, @Param("listingId") UUID listingId);

    @Modifying
    @Query("delete from CartItem i where i.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
