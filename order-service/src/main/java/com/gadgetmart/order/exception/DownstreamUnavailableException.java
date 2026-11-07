package com.gadgetmart.order.exception;

/** A dependency (Cart / Catalog) timed out, refused the connection or returned an unexpected status. */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownstreamUnavailableException(String message) {
        super(message);
    }
}
