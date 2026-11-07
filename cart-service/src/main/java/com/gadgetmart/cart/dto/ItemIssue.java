package com.gadgetmart.cart.dto;

public enum ItemIssue {
    NO_LONGER_AVAILABLE,   // listing removed or deactivated: blocks checkout
    OUT_OF_STOCK,          // sold out: blocks checkout
    INSUFFICIENT_STOCK,    // fewer units left than in the cart: blocks checkout
    PRICE_CHANGED          // informational: checkout simply uses the current price
}
