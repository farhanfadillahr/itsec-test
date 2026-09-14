package com.itsectest.shared.error;

public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }
}
