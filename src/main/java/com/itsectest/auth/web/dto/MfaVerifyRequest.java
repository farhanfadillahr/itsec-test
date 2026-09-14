package com.itsectest.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "MfaVerifyRequest")
public record MfaVerifyRequest(

        @NotBlank
        @Schema(description = "Value returned by the login call", example = "3f2c8d1e-...")
        String challengeId,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4,8}$", message = "must be the numeric code from the email")
        @Schema(example = "482913")
        String code) {
}
