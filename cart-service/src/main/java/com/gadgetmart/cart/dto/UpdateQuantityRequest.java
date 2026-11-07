package com.gadgetmart.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateQuantityRequest(@Min(1) @Max(10) int quantity) {
}
