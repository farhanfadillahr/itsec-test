package com.itsectest.auth.web.dto;

import com.itsectest.auth.internal.mfa.OtpIssued;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MfaChallengeResponse", description = "A one-time code has been emailed")
public record MfaChallengeResponse(
        String challengeId,
        @Schema(example = "f****n@example.com") String sentTo,
        @Schema(description = "Seconds until the code expires", example = "300") long expiresIn) {

    public static MfaChallengeResponse from(OtpIssued issued) {
        return new MfaChallengeResponse(issued.challengeId(), issued.maskedEmail(), issued.expiresIn());
    }
}
