package com.itsectest.auth.web.dto;

import java.util.UUID;

import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfileResponse", description = "The signed-in account")
public record ProfileResponse(
        UUID id,
        String fullname,
        String username,
        String email,
        Role role,
        boolean mfaEnabled) {

    public static ProfileResponse from(UserAccount account) {
        return new ProfileResponse(account.id(), account.fullname(), account.username(),
                account.email(), account.role(), account.mfaEnabled());
    }
}
