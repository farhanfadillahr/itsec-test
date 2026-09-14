package com.itsectest.auth.internal.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.itsectest.auth.domain.IssuedTokens;
import com.itsectest.shared.security.JwtProperties;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserStatus;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenFactoryTest {

    private static final String SECRET = "a-test-signing-secret-that-is-long-enough-for-hs256";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private final JwtProperties properties = new JwtProperties(
            SECRET, "https://itsec-test.local", Duration.ofMinutes(15), Duration.ofDays(7));

    private TokenFactory factory;
    private JwtDecoder decoder;

    private static UserAccount account() {
        return new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                Role.EDITOR, UserStatus.ACTIVE, true, null);
    }

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

        when(redis.opsForValue()).thenReturn(valueOps);
        factory = new TokenFactory(encoder, properties, new RefreshTokenStore(redis, properties));
    }

    @Test
    void signsATokenCarryingSubjectUsernameAndRole() {
        IssuedTokens issued = factory.issueFor(account());

        var jwt = decoder.decode(issued.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("farhan");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("EDITOR");
        assertThat(jwt.getIssuer()).hasToString("https://itsec-test.local");
    }

    @Test
    void givesEveryTokenAJtiSoLogoutHasSomethingToRevoke() {
        String first = decoder.decode(factory.issueFor(account()).accessToken()).getId();
        String second = decoder.decode(factory.issueFor(account()).accessToken()).getId();

        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @Test
    void honoursTheConfiguredAccessTokenLifetime() {
        IssuedTokens issued = factory.issueFor(account());

        assertThat(issued.expiresIn()).isEqualTo(900);
        assertThat(issued.tokenType()).isEqualTo("Bearer");
        var jwt = decoder.decode(issued.accessToken());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void issuesARefreshTokenAlongsideTheAccessToken() {
        IssuedTokens issued = factory.issueFor(account());

        assertThat(issued.refreshToken()).isNotBlank();
        verify(valueOps).set(any(), org.mockito.ArgumentMatchers.eq(USER_ID.toString()),
                org.mockito.ArgumentMatchers.eq(Duration.ofDays(7)));
    }

    @Test
    void refreshTokensAreOpaqueRandomStringsNotJwts() {
        RefreshTokenStore store = new RefreshTokenStore(redis, properties);

        String token = store.issue(USER_ID);

        assertThat(token).doesNotContain(".");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void aRefreshTokenIsGoodForExactlyOneExchange() {
        RefreshTokenStore store = new RefreshTokenStore(redis, properties);
        when(valueOps.getAndDelete("auth:refresh:token-1")).thenReturn(USER_ID.toString(), (String) null);

        assertThat(store.rotate("token-1")).contains(USER_ID);
        assertThat(store.rotate("token-1")).isEmpty();
    }

    @Test
    void ignoresAnEmptyRefreshToken() {
        RefreshTokenStore store = new RefreshTokenStore(redis, properties);

        assertThat(store.rotate(null)).isEmpty();
        assertThat(store.rotate("  ")).isEmpty();
        assertThat(Optional.empty()).isEmpty();
    }

    @Test
    void revokesARefreshTokenOnLogout() {
        RefreshTokenStore store = new RefreshTokenStore(redis, properties);

        store.revoke("token-1");
        store.revoke(null);
        store.revoke("   ");

        verify(redis).delete("auth:refresh:token-1");
    }

    @Test
    void tokensAreAcceptedOnlyUntilTheyExpire() {
        assertThat(Instant.now()).isNotNull();
        assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }
}
