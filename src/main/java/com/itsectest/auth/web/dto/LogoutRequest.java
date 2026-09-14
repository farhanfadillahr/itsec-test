package com.itsectest.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LogoutRequest")
public record LogoutRequest(
        @Schema(description = "Optional: also invalidates this refresh token", nullable = true)
        String refreshToken) {
}
