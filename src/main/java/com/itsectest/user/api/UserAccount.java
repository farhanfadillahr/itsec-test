package com.itsectest.user.api;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.shared.security.Role;
import com.itsectest.user.domain.UserStatus;

public record UserAccount(
        UUID id,
        String fullname,
        String username,
        String email,
        Role role,
        UserStatus status,
        boolean mfaEnabled,
        Instant lockedUntil) {

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
