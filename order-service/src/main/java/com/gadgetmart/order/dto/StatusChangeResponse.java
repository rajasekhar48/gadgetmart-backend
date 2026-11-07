package com.gadgetmart.order.dto;

import java.time.Instant;

public record StatusChangeResponse(String from, String to, String reason, Instant at) {
}
