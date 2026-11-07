package com.gadgetmart.order.dto;

import java.util.UUID;

/** Mirrors Catalog's 409 body entries. */
public record UnavailableItem(UUID listingId, int requested, int available) {
}
