package com.gadgetmart.catalog.exception;

import com.gadgetmart.catalog.dto.UnavailableItem;
import lombok.Getter;

import java.util.List;

/** Thrown from inside the reserve transaction so every partial stock change is rolled back (all-or-nothing). */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final List<UnavailableItem> items;

    public InsufficientStockException(List<UnavailableItem> items) {
        super("One or more items are no longer available");
        this.items = items;
    }
}
