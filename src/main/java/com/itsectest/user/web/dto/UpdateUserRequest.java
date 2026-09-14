package com.itsectest.user.web.dto;

import com.itsectest.shared.security.Role;
import com.itsectest.shared.web.validation.StrongPassword;
import com.itsectest.user.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateUserRequest")
public record UpdateUserRequest(

        @NotBlank @Size(max = 150) String fullname,

        @NotBlank @Email @Size(max = 255) String email,

        @NotNull Role role,

        @NotNull UserStatus status,

        boolean mfaEnabled,

        @StrongPassword
        @Schema(description = "Omit to keep the current password", nullable = true)
        String password) {
}
