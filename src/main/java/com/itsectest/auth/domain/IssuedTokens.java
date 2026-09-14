package com.itsectest.auth.domain;

public record IssuedTokens(String accessToken, String refreshToken, long expiresIn, String tokenType) {

    public static IssuedTokens bearer(String accessToken, String refreshToken, long expiresIn) {
        return new IssuedTokens(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
