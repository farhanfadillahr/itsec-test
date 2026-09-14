package com.itsectest.auth.internal.mfa;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mfa")
public record MfaProperties(
        boolean enabled,
        int otpLength,
        Duration otpTtl,
        int maxVerifyAttempts,
        Duration resendCooldown) {

    public MfaProperties {
        if (otpLength < 4) {
            otpLength = 6;
        }
        if (otpTtl == null) {
            otpTtl = Duration.ofMinutes(5);
        }
        if (maxVerifyAttempts <= 0) {
            maxVerifyAttempts = 3;
        }
        if (resendCooldown == null) {
            resendCooldown = Duration.ofSeconds(60);
        }
    }
}
