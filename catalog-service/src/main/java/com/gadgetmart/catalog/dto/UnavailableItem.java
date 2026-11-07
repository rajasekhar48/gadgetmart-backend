package com.gadgetmart.catalog.dto;

import java.util.UUID;

public record UnavailableItem(UUID listingId, int requested, int available) {
}
