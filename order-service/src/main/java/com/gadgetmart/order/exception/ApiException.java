package com.gadgetmart.order.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** Business error with a machine-readable code and optional extra details for the client. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public static ApiException notFound(String code, String m) { return new ApiException(HttpStatus.NOT_FOUND, code, m); }

    public static ApiException conflict(String code, String m) { return new ApiException(HttpStatus.CONFLICT, code, m); }

    public static ApiException badRequest(String code, String m) { return new ApiException(HttpStatus.BAD_REQUEST, code, m); }
}
