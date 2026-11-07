package com.gadgetmart.order.exception;

/** The stock hold expired or was released before we could commit it. */
public class ReservationLostException extends RuntimeException {

    public ReservationLostException() {
        super("The stock reservation is no longer valid");
    }
}
