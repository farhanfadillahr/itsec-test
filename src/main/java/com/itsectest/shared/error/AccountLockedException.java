package com.itsectest.shared.error;

public class AccountLockedException extends ApiException {

    public AccountLockedException(String message) {
        super(ErrorCode.ACCOUNT_LOCKED, message);
    }
}
