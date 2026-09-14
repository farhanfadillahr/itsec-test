package com.itsectest.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest")
public record RefreshRequest(
        @NotBlank @Schema(description = "The refresh token from a previous login") String refreshToken) {
}
