package com.itsectest.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DenylistJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenDenylist denylist;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (denylist.isRevoked(token.getId())) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
