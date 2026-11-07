package com.gadgetmart.cart.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Business error with an optional machine-readable code the UI can switch on. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String code, String m) { return new ApiException(HttpStatus.NOT_FOUND, code, m); }

    public static ApiException conflict(String code, String m) { return new ApiException(HttpStatus.CONFLICT, code, m); }
}
