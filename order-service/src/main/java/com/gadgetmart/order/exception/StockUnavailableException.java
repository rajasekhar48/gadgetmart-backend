package com.gadgetmart.order.exception;

import com.gadgetmart.order.dto.UnavailableItem;
import lombok.Getter;

import java.util.List;

/** Catalog answered 409: one or more items cannot be reserved. A business outcome, not a failure. */
@Getter
public class StockUnavailableException extends RuntimeException {

    private final List<UnavailableItem> items;

    public StockUnavailableException(List<UnavailableItem> items) {
        super("One or more items are no longer available");
        this.items = items;
    }
}
