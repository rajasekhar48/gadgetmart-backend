package com.gadgetmart.cart.dto;

import java.util.List;

public record CartView(
        List<CartItemView> items,
        int itemCount,              // total units
        int lineCount,              // distinct items
        long subtotalPaise,         // purchasable lines only, at current prices
        String currency,
        boolean checkoutReady       // not empty and every line purchasable
) {
    public static CartView empty() {
        return new CartView(List.of(), 0, 0, 0, "INR", false);
    }
}
