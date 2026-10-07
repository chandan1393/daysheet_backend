package com.daysheet.config;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    /** Extra machine-readable details for the frontend, e.g. reason=EMAIL_NOT_VERIFIED. */
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields == null ? Map.of() : fields;
    }

    public HttpStatus getStatus() { return status; }

    public Map<String, String> getFields() { return fields; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
