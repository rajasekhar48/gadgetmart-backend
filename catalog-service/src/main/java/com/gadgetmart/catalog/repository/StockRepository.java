package com.gadgetmart.catalog.repository;

import com.gadgetmart.catalog.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * All stock changes are single conditional UPDATEs. The WHERE clause is the guard, so two buyers
 * can never both take the last unit, with no read-modify-write race and no explicit locks.
 * Each returns the number of rows changed: 0 means the guard failed.
 */
public interface StockRepository extends JpaRepository<Stock, UUID> {

    /** available -> reserved. Only for ACTIVE listings with enough available units. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s
               set s.availableQty = s.availableQty - :qty,
                   s.reservedQty = s.reservedQty + :qty,
                   s.updatedAt = :now
             where s.listingId = :id
               and s.availableQty >= :qty
               and exists (select 1 from Listing l
                            where l.id = s.listingId
                              and l.status = com.gadgetmart.catalog.domain.ListingStatus.ACTIVE)
            """)
    int reserve(@Param("id") UUID id, @Param("qty") int qty, @Param("now") Instant now);

    /** reserved -> sold (units simply leave the reserved pool). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s
               set s.reservedQty = s.reservedQty - :qty,
                   s.updatedAt = :now
             where s.listingId = :id
               and s.reservedQty >= :qty
            """)
    int commit(@Param("id") UUID id, @Param("qty") int qty, @Param("now") Instant now);

    /** reserved -> available (cancel / expiry). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s
               set s.availableQty = s.availableQty + :qty,
                   s.reservedQty = s.reservedQty - :qty,
                   s.updatedAt = :now
             where s.listingId = :id
               and s.reservedQty >= :qty
            """)
    int release(@Param("id") UUID id, @Param("qty") int qty, @Param("now") Instant now);
}
