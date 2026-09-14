package com.itsectest.auth.internal.mfa;

import java.time.Duration;

import com.itsectest.user.api.UserAccount;

public interface OtpChannel {

    void deliver(UserAccount account, String code, Duration validFor);

    String name();
}
