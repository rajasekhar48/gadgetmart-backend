package com.gadgetmart.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,   // created, stock held, payment in progress
    PAID,              // payment captured, stock committed: the order is placed
    SHIPPED,           // (seller / ops flow, later)
    DELIVERED,
    COMPLETED,         // return window closed
    REJECTED,          // stock was not available
    CANCELLED          // payment failed, timed out or compensated
}
