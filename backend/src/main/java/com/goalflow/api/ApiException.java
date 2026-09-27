package com.goalflow.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer currentVersion;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(
            HttpStatus status, String code, String message, Integer currentVersion) {
        super(message);
        this.status = status;
        this.code = code;
        this.currentVersion = currentVersion;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }
}
