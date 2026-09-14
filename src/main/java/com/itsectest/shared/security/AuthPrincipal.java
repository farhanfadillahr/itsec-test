package com.itsectest.shared.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthPrincipal(UUID userId, String username, Role role) {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";

    public static AuthPrincipal from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String role = jwt.getClaimAsString(CLAIM_ROLE);
        if (role == null) {
            return null;
        }
        return new AuthPrincipal(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_USERNAME),
                Role.valueOf(role));
    }

    public boolean isSuperAdmin() {
        return role.isSuperAdmin();
    }

    public boolean owns(UUID authorId) {
        return userId.equals(authorId);
    }
}
