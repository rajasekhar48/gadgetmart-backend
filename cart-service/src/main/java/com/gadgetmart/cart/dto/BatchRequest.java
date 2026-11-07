package com.gadgetmart.cart.dto;

import java.util.List;
import java.util.UUID;

public record BatchRequest(List<UUID> ids) {
}
