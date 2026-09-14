package com.itsectest.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChallengeRequest", description = "Identifies a pending MFA challenge")
public record ChallengeRequest(@NotBlank String challengeId) {
}
