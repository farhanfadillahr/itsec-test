package com.itsectest.user.internal;

import com.itsectest.shared.security.Role;

public record CreateUserCommand(
        String fullname,
        String username,
        String email,
        String rawPassword,
        Role role,
        boolean mfaEnabled) {
}
