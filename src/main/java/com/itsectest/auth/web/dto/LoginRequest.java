package com.itsectest.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @NotBlank @Size(max = 255)
        @Schema(description = "Username or email",
                example = "superadmin")
        String identifier,

        @NotBlank @Size(max = 72)
        @Schema(example = "SuperAdmin#2026")
        String password) {
}
