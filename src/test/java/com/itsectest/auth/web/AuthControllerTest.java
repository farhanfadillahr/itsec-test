package com.itsectest.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.itsectest.auth.domain.IssuedTokens;
import com.itsectest.auth.domain.LoginOutcome;
import com.itsectest.auth.internal.AuthService;
import com.itsectest.auth.internal.mfa.OtpIssued;
import com.itsectest.auth.web.dto.ChallengeRequest;
import com.itsectest.auth.web.dto.LoginRequest;
import com.itsectest.auth.web.dto.LogoutRequest;
import com.itsectest.auth.web.dto.MfaVerifyRequest;
import com.itsectest.auth.web.dto.RefreshRequest;
import com.itsectest.auth.web.dto.RegisterRequest;
import com.itsectest.shared.error.UnauthorizedException;
import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;
import com.itsectest.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private AuthService auth;
    @Mock private UserFacade users;
    @Mock private CurrentUserProvider currentUser;

    @InjectMocks private AuthController controller;

    private static UserAccount account() {
        return new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                Role.EDITOR, UserStatus.ACTIVE, true, null);
    }

    @Test
    void registrationAnswersWithCreatedAndTheNewProfile() {
        when(auth.register(any())).thenReturn(account());

        var response = controller.register(
                new RegisterRequest("Farhan", "farhan", "farhan@example.com", "Str0ng#Pass"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().username()).isEqualTo("farhan");
        assertThat(response.getBody().success()).isTrue();
    }

    @Test
    void loginReturnsTheChallengeWhenASecondFactorIsNeeded() {
        when(auth.login("farhan", "Str0ng#Pass")).thenReturn(
                new LoginOutcome.MfaChallenge("challenge-1", "f****n@example.com", 300));

        var body = controller.login(new LoginRequest("farhan", "Str0ng#Pass")).data();

        assertThat(body.mfaRequired()).isTrue();
        assertThat(body.mfa().challengeId()).isEqualTo("challenge-1");
        assertThat(body.tokens()).isNull();
    }

    @Test
    void loginReturnsTokensWhenNoSecondFactorIsNeeded() {
        when(auth.login("farhan", "Str0ng#Pass")).thenReturn(
                new LoginOutcome.Authenticated(IssuedTokens.bearer("access", "refresh", 900)));

        var body = controller.login(new LoginRequest("farhan", "Str0ng#Pass")).data();

        assertThat(body.mfaRequired()).isFalse();
        assertThat(body.tokens().accessToken()).isEqualTo("access");
        assertThat(body.mfa()).isNull();
    }

    @Test
    void verifyingTheCodeReturnsTokens() {
        when(auth.verifyMfa("challenge-1", "482913"))
                .thenReturn(IssuedTokens.bearer("access", "refresh", 900));

        assertThat(controller.verifyMfa(new MfaVerifyRequest("challenge-1", "482913")).data().accessToken())
                .isEqualTo("access");
    }

    @Test
    void resendReturnsTheSameChallengeId() {
        when(auth.resendOtp("challenge-1")).thenReturn(new OtpIssued("challenge-1", "f****n@example.com", 300));

        assertThat(controller.resendOtp(new ChallengeRequest("challenge-1")).data().challengeId())
                .isEqualTo("challenge-1");
    }

    @Test
    void refreshReturnsANewPair() {
        when(auth.refresh("old")).thenReturn(IssuedTokens.bearer("new-access", "new-refresh", 900));

        assertThat(controller.refresh(new RefreshRequest("old")).data().refreshToken())
                .isEqualTo("new-refresh");
    }

    @Test
    void logoutRevokesBothTokens() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .jti("jti-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("role", "EDITOR").build();

        assertThat(controller.logout(jwt, new LogoutRequest("refresh")).success()).isTrue();
        verify(auth).logout("jti-1", jwt.getExpiresAt(), "refresh");
    }

    @Test
    void logoutWorksWithNoBodyAtAll() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .jti("jti-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("role", "EDITOR").build();

        controller.logout(jwt, null);

        verify(auth).logout("jti-1", jwt.getExpiresAt(), null);
    }

    @Test
    void logoutWithoutATokenIsUnauthorised() {
        assertThatThrownBy(() -> controller.logout(null, null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void meReturnsTheSignedInProfile() {
        when(currentUser.require()).thenReturn(new AuthPrincipal(USER_ID, "farhan", Role.EDITOR));
        when(users.findById(USER_ID)).thenReturn(Optional.of(account()));

        assertThat(controller.me().data().email()).isEqualTo("farhan@example.com");
    }

    @Test
    void meFailsIfTheAccountWasDeletedWhileTheTokenIsStillValid() {
        when(currentUser.require()).thenReturn(new AuthPrincipal(USER_ID, "farhan", Role.EDITOR));
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.me()).isInstanceOf(UnauthorizedException.class);
    }
}
