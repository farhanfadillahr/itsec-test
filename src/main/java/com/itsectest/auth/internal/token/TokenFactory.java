package com.itsectest.auth.internal.token;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.itsectest.auth.domain.IssuedTokens;
import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.JwtProperties;
import com.itsectest.user.api.UserAccount;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenFactory {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final RefreshTokenStore refreshTokens;

    public IssuedTokens issueFor(UserAccount account) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(account.id().toString())
                .id(UUID.randomUUID().toString())
                .claim(AuthPrincipal.CLAIM_USERNAME, account.username())
                .claim(AuthPrincipal.CLAIM_ROLE, account.role().name())
                .build();

        String accessToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return IssuedTokens.bearer(accessToken, refreshTokens.issue(account.id()),
                properties.accessTokenTtl().toSeconds());
    }
}
