package com.itsectest.user.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse")
public record UserResponse(
        UUID id,
        String fullname,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean mfaEnabled,
        Instant lockedUntil) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.id(),
                account.fullname(),
                account.username(),
                account.email(),
                account.role(),
                account.status(),
                account.mfaEnabled(),
                account.lockedUntil());
    }
}
