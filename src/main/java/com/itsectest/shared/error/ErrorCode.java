package com.itsectest.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    OTP_INVALID(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    MAIL_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
