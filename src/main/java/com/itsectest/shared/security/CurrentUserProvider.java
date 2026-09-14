package com.itsectest.shared.security;

import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.itsectest.shared.error.UnauthorizedException;

@Component
public class CurrentUserProvider {

    public Optional<AuthPrincipal> find() {
        return Optional.ofNullable(
                AuthPrincipal.from(SecurityContextHolder.getContext().getAuthentication()));
    }

    public AuthPrincipal require() {
        return find().orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }
}
