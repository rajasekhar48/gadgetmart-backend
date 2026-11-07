package com.gadgetmart.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Changed only through the atomic UPDATE statements in StockRepository, never by setters. */
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor
public class Stock {

    @Id
    @Column(name = "listing_id")
    private UUID listingId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
