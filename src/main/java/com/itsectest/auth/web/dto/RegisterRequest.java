package com.itsectest.auth.web.dto;

import com.itsectest.shared.web.validation.StrongPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Self-service sign-up. The new account is always a VIEWER.")
public record RegisterRequest(

        @NotBlank @Size(max = 150)
        @Schema(example = "Farhan Fadillah Rafi")
        String fullname,

        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "may contain only letters, digits, dot, underscore and hyphen")
        @Schema(example = "farhan")
        String username,

        @NotBlank @Email @Size(max = 255)
        @Schema(example = "farhan@example.com")
        String email,

        @NotBlank @StrongPassword
        @Schema(example = "Str0ng#Pass")
        String password) {
}
