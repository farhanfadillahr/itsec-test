package com.itsectest.auth.internal.mfa;

public record OtpIssued(String challengeId, String maskedEmail, long expiresIn) {
}
