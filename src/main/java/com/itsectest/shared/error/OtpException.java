package com.itsectest.shared.error;

public class OtpException extends ApiException {

    public OtpException(String message) {
        super(ErrorCode.OTP_INVALID, message);
    }
}
