package com.itsectest.user.internal;

import com.itsectest.shared.security.Role;
import com.itsectest.user.domain.UserStatus;

public record UpdateUserCommand(
        String fullname,
        String email,
        Role role,
        UserStatus status,
        boolean mfaEnabled,
        String rawPassword) {
}
