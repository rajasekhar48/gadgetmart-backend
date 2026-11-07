package com.gadgetmart.catalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String m)   { return new ApiException(HttpStatus.NOT_FOUND, m); }

    public static ApiException conflict(String m)   { return new ApiException(HttpStatus.CONFLICT, m); }

    public static ApiException badRequest(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
}
