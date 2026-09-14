package com.itsectest.auth.internal.lockout;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.lockout")
public record LockoutProperties(int maxAttempts, Duration window, Duration duration) {

    public LockoutProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (window == null) {
            window = Duration.ofMinutes(10);
        }
        if (duration == null) {
            duration = Duration.ofMinutes(30);
        }
    }
}
