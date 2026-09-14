package com.itsectest.auth.web.dto;

import com.itsectest.auth.domain.IssuedTokens;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResponse")
public record TokenResponse(
        String accessToken,
        String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn) {

    public static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(),
                tokens.tokenType(), tokens.expiresIn());
    }
}
