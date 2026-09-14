package com.itsectest.shared.error;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
