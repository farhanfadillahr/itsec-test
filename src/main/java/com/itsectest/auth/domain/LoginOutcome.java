package com.itsectest.auth.domain;

public sealed interface LoginOutcome {

    record Authenticated(IssuedTokens tokens) implements LoginOutcome {
    }

    record MfaChallenge(String challengeId, String maskedEmail, long expiresIn) implements LoginOutcome {
    }
}
