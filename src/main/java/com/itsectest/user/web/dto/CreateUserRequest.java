package com.itsectest.user.web.dto;

import com.itsectest.shared.security.Role;
import com.itsectest.shared.web.validation.StrongPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateUserRequest")
public record CreateUserRequest(

        @NotBlank
        @Size(max = 150)
        @Schema(example = "Farhan Fadillah Rafi")
        String fullname,

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "may contain only letters, digits, dot, underscore and hyphen")
        @Schema(example = "farhan.editor")
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(example = "farhan.editor@example.com")
        String email,

        @NotBlank
        @StrongPassword
        @Schema(example = "Editor#2026")
        String password,

        @NotNull
        @Schema(example = "EDITOR")
        Role role,

        @Schema(description = "Require an email OTP at login", example = "true")
        boolean mfaEnabled) {
}
