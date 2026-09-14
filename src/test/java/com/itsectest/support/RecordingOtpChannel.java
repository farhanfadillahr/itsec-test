package com.itsectest.support;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.itsectest.auth.internal.mfa.OtpChannel;
import com.itsectest.user.api.UserAccount;

@TestConfiguration(proxyBeanMethods = false)
public class RecordingOtpChannel {

    private final ConcurrentMap<String, String> codes = new ConcurrentHashMap<>();

    @Bean
    @Primary
    OtpChannel recordingOtpChannel() {
        return new OtpChannel() {

            @Override
            public void deliver(UserAccount account, String code, Duration validFor) {
                codes.put(account.email(), code);
            }

            @Override
            public String name() {
                return "EMAIL";
            }
        };
    }

    @Bean
    CapturedCodes capturedCodes() {
        return codes::get;
    }

    @FunctionalInterface
    public interface CapturedCodes {
        String forEmail(String email);
    }
}
