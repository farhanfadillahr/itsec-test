package com.itsectest.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LoginResponse")
public record LoginResponse(
        @Schema(description = "True when a second factor is still required") boolean mfaRequired,
        TokenResponse tokens,
        MfaChallengeResponse mfa) {

    public static LoginResponse authenticated(TokenResponse tokens) {
        return new LoginResponse(false, tokens, null);
    }

    public static LoginResponse challenge(MfaChallengeResponse challenge) {
        return new LoginResponse(true, null, challenge);
    }
}
