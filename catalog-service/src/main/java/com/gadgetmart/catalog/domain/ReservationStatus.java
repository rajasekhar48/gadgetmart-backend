package com.gadgetmart.catalog.domain;

public enum ReservationStatus {
    HELD,        // stock set aside, waiting for payment
    COMMITTED,   // payment done, stock is sold
    RELEASED,    // order cancelled, stock returned
    EXPIRED      // hold timed out, stock returned
}
