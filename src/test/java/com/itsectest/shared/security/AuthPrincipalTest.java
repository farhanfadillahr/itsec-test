package com.itsectest.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthPrincipalTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claims(existing -> existing.putAll(claims))
                .build();
    }

    @Test
    void readsSubjectUsernameAndRoleFromTheToken() {
        Jwt token = jwt(Map.of("sub", USER_ID.toString(), "username", "farhan", "role", "EDITOR"));

        AuthPrincipal principal = AuthPrincipal.from(
                new UsernamePasswordAuthenticationToken(token, null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.username()).isEqualTo("farhan");
        assertThat(principal.role()).isEqualTo(Role.EDITOR);
        assertThat(principal.isSuperAdmin()).isFalse();
        assertThat(principal.owns(USER_ID)).isTrue();
        assertThat(principal.owns(UUID.randomUUID())).isFalse();
    }

    @Test
    void recognisesASuperAdmin() {
        Jwt token = jwt(Map.of("sub", USER_ID.toString(), "username", "root", "role", "SUPER_ADMIN"));

        assertThat(AuthPrincipal.from(new UsernamePasswordAuthenticationToken(token, null)).isSuperAdmin())
                .isTrue();
    }

    @Test
    void yieldsNothingForAnUnauthenticatedRequest() {
        assertThat(AuthPrincipal.from(null)).isNull();
    }

    @Test
    void yieldsNothingWhenThePrincipalIsNotAToken() {
        assertThat(AuthPrincipal.from(new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))).isNull();
    }

    @Test
    void yieldsNothingWhenTheRoleClaimIsMissing() {
        Jwt token = jwt(Map.of("sub", USER_ID.toString(), "username", "farhan"));

        assertThat(AuthPrincipal.from(new UsernamePasswordAuthenticationToken(token, null))).isNull();
    }

    @Test
    void mapsRolesToSpringAuthorityNames() {
        assertThat(Role.SUPER_ADMIN.authority()).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(Role.VIEWER.authority()).isEqualTo("ROLE_VIEWER");
        assertThat(Role.VIEWER.isSuperAdmin()).isFalse();
    }
}
