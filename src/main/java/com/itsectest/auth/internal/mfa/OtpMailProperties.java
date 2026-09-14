package com.itsectest.auth.internal.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record OtpMailProperties(String from, boolean deliveryEnabled) {

    public OtpMailProperties {
        if (from == null || from.isBlank()) {
            from = "no-reply@itsec-test.local";
        }
    }
}
