package com.gadgetmart.cart.exception;

/** Catalog timed out, refused the connection or returned 5xx. Counted by the circuit breaker and retried. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogUnavailableException(String message) {
        super(message);
    }
}
