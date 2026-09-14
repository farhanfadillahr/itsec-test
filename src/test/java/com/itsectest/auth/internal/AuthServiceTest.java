package com.itsectest.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itsectest.auth.domain.IssuedTokens;
import com.itsectest.auth.domain.LoginOutcome;
import com.itsectest.auth.internal.identity.IdentifierResolver;
import com.itsectest.auth.internal.lockout.LockoutState;
import com.itsectest.auth.internal.lockout.LoginAttemptGuard;
import com.itsectest.auth.internal.mfa.MfaProperties;
import com.itsectest.auth.internal.mfa.OtpIssued;
import com.itsectest.auth.internal.mfa.OtpService;
import com.itsectest.auth.internal.token.RefreshTokenStore;
import com.itsectest.auth.internal.token.TokenFactory;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.error.AccountLockedException;
import com.itsectest.shared.error.ApiException;
import com.itsectest.shared.error.ErrorCode;
import com.itsectest.shared.error.InvalidCredentialsException;
import com.itsectest.shared.error.OtpException;
import com.itsectest.shared.security.Role;
import com.itsectest.shared.security.TokenDenylist;
import com.itsectest.user.api.RegisterUserCommand;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;
import com.itsectest.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PASSWORD = "Str0ng#Pass";

    @Mock private UserFacade users;
    @Mock private IdentifierResolver identifiers;
    @Mock private LoginAttemptGuard lockoutGuard;
    @Mock private OtpService otp;
    @Mock private TokenFactory tokens;
    @Mock private RefreshTokenStore refreshTokens;
    @Mock private AuditPublisher audit;
    @Mock private TokenDenylist denylist;

    private AuthService auth;

    private static UserAccount account(boolean mfaEnabled, UserStatus status) {
        return new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                Role.EDITOR, status, mfaEnabled, null);
    }

    private void withMfa(boolean globallyEnabled) {
        auth = new AuthService(users, identifiers, lockoutGuard, otp, tokens, refreshTokens,
                new MfaProperties(globallyEnabled, 6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60)),
                audit, denylist);
    }

    @BeforeEach
    void setUp() {
        withMfa(true);
        when(lockoutGuard.lockRemaining(any())).thenReturn(Optional.empty());
        when(lockoutGuard.lockoutDuration()).thenReturn(Duration.ofMinutes(30));
        when(tokens.issueFor(any())).thenReturn(IssuedTokens.bearer("access", "refresh", 900));
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        void issuesAnOtpChallengeWhenMfaIsOn() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(true);
            when(otp.issue(any())).thenReturn(new OtpIssued("challenge-1", "f****n@example.com", 300));

            LoginOutcome outcome = auth.login("farhan", PASSWORD);

            assertThat(outcome).isInstanceOf(LoginOutcome.MfaChallenge.class);
            LoginOutcome.MfaChallenge challenge = (LoginOutcome.MfaChallenge) outcome;
            assertThat(challenge.challengeId()).isEqualTo("challenge-1");
            verify(tokens, never()).issueFor(any());
        }

        @Test
        void returnsTokensDirectlyWhenTheAccountHasMfaOff() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(false, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(true);

            LoginOutcome outcome = auth.login("farhan", PASSWORD);

            assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
            assertThat(((LoginOutcome.Authenticated) outcome).tokens().accessToken()).isEqualTo("access");
        }

        @Test
        void skipsMfaEntirelyWhenItIsSwitchedOffGlobally() {
            withMfa(false);
            when(tokens.issueFor(any())).thenReturn(IssuedTokens.bearer("access", "refresh", 900));
            when(lockoutGuard.lockRemaining(any())).thenReturn(Optional.empty());
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(true);

            assertThat(auth.login("farhan", PASSWORD)).isInstanceOf(LoginOutcome.Authenticated.class);
            verify(otp, never()).issue(any());
        }

        @Test
        void clearsTheFailureCounterOnSuccess() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(false, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(true);

            auth.login("farhan", PASSWORD);

            verify(lockoutGuard).reset(USER_ID);
        }

        @Test
        void givesTheSameAnswerForAnUnknownUserAsForAWrongPassword() {
            when(identifiers.resolve("ghost")).thenReturn(Optional.empty());
            when(identifiers.kindOf("ghost")).thenReturn("USERNAME");

            assertThatThrownBy(() -> auth.login("ghost", PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Username, email or password is incorrect");
        }

        @Test
        void recordsAFailedLoginForAnUnknownIdentifier() {
            when(identifiers.resolve("ghost")).thenReturn(Optional.empty());
            when(identifiers.kindOf("ghost")).thenReturn("EMAIL");

            assertThatThrownBy(() -> auth.login("ghost", PASSWORD)).isInstanceOf(RuntimeException.class);

            verify(audit).recordForSubject(eq(null), eq("ghost"), eq(AuditAction.LOGIN_FAILED),
                    eq(AuditStatus.FAILURE), any());
        }

        @Test
        void countsAWrongPasswordTowardsTheLockout() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(false);
            when(lockoutGuard.recordFailure(USER_ID)).thenReturn(new LockoutState(2, 3, null));

            assertThatThrownBy(() -> auth.login("farhan", PASSWORD))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(lockoutGuard).recordFailure(USER_ID);
            verify(users, never()).lockUntil(any(), any());
        }

        @Test
        void locksTheAccountOnTheFifthFailure() {
            Instant until = Instant.now().plus(Duration.ofMinutes(30));
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(false);
            when(lockoutGuard.recordFailure(USER_ID)).thenReturn(new LockoutState(5, 0, until));

            assertThatThrownBy(() -> auth.login("farhan", PASSWORD))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessageContaining("30 minute(s)");

            verify(users).lockUntil(USER_ID, until);
            ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.captor();
            verify(audit).recordForSubject(eq(USER_ID), eq("farhan"), eq(AuditAction.ACCOUNT_LOCKED),
                    eq(AuditStatus.FAILURE), detail.capture());
            assertThat(detail.getValue()).containsEntry("failedAttempts", 5);
        }

        @Test
        void refusesALockedAccountBeforeCheckingThePassword() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(lockoutGuard.lockRemaining(USER_ID)).thenReturn(Optional.of(Duration.ofMinutes(12)));

            assertThatThrownBy(() -> auth.login("farhan", PASSWORD))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessageContaining("12 minute(s)");

            verify(users, never()).matchesPassword(any(), anyString());
        }

        @Test
        void refusesADisabledAccount() {
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(account(true, UserStatus.DISABLED)));

            assertThatThrownBy(() -> auth.login("farhan", PASSWORD))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).getCode())
                    .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        }

        @Test
        void liftsAStaleDatabaseLockOnASuccessfulLogin() {
            UserAccount expired = new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                    Role.EDITOR, UserStatus.LOCKED, false, Instant.now().plusSeconds(60));
            when(identifiers.resolve("farhan")).thenReturn(Optional.of(expired));
            when(users.matchesPassword(USER_ID, PASSWORD)).thenReturn(true);

            auth.login("farhan", PASSWORD);

            verify(users).clearLock(USER_ID);
        }
    }

    @Nested
    @DisplayName("MFA verification")
    class Mfa {

        @Test
        void exchangesACorrectCodeForTokens() {
            when(otp.verify("challenge-1", "482913")).thenReturn(USER_ID);
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));

            IssuedTokens issued = auth.verifyMfa("challenge-1", "482913");

            assertThat(issued.accessToken()).isEqualTo("access");
            assertThat(issued.tokenType()).isEqualTo("Bearer");
            verify(audit).recordForSubject(eq(USER_ID), eq("farhan"), eq(AuditAction.OTP_VERIFIED),
                    eq(AuditStatus.SUCCESS), any());
        }

        @Test
        void auditsAndRethrowsAWrongCode() {
            when(otp.verify("challenge-1", "000000")).thenThrow(new OtpException("Incorrect code."));
            when(otp.ownerOf("challenge-1")).thenReturn(USER_ID);

            assertThatThrownBy(() -> auth.verifyMfa("challenge-1", "000000"))
                    .isInstanceOf(OtpException.class);

            verify(audit).recordForSubject(eq(USER_ID), eq(null), eq(AuditAction.OTP_FAILED),
                    eq(AuditStatus.FAILURE), any());
        }

        @Test
        void stillAuditsWhenTheChallengeItselfIsGone() {
            when(otp.verify("gone", "000000")).thenThrow(new OtpException("expired"));
            when(otp.ownerOf("gone")).thenThrow(new OtpException("expired"));

            assertThatThrownBy(() -> auth.verifyMfa("gone", "000000")).isInstanceOf(OtpException.class);

            verify(audit).recordForSubject(eq(null), eq(null), eq(AuditAction.OTP_FAILED),
                    eq(AuditStatus.FAILURE), any());
        }

        @Test
        void refusesWhenTheAccountDisappearedMidChallenge() {
            when(otp.verify("challenge-1", "482913")).thenReturn(USER_ID);
            when(users.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auth.verifyMfa("challenge-1", "482913"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void resendsACodeForAnExistingChallenge() {
            when(otp.ownerOf("challenge-1")).thenReturn(USER_ID);
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));
            when(otp.resend(eq("challenge-1"), any()))
                    .thenReturn(new OtpIssued("challenge-1", "f****n@example.com", 300));

            assertThat(auth.resendOtp("challenge-1").challengeId()).isEqualTo("challenge-1");
            verify(audit).recordForSubject(eq(USER_ID), eq("farhan"), eq(AuditAction.OTP_RESENT),
                    eq(AuditStatus.SUCCESS), any());
        }

        @Test
        void refusesToResendForAVanishedAccount() {
            when(otp.ownerOf("challenge-1")).thenReturn(USER_ID);
            when(users.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auth.resendOtp("challenge-1")).isInstanceOf(OtpException.class);
        }
    }

    @Nested
    @DisplayName("sessions")
    class Sessions {

        @Test
        void rotatesTheRefreshToken() {
            when(refreshTokens.rotate("old-token")).thenReturn(Optional.of(USER_ID));
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(true, UserStatus.ACTIVE)));

            assertThat(auth.refresh("old-token").accessToken()).isEqualTo("access");
            verify(audit).recordForSubject(eq(USER_ID), eq("farhan"), eq(AuditAction.TOKEN_REFRESHED),
                    eq(AuditStatus.SUCCESS), eq(null));
        }

        @Test
        void refusesARefreshTokenThatWasAlreadyUsed() {
            when(refreshTokens.rotate("replayed")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auth.refresh("replayed"))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).getCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }

        @Test
        void refusesToRefreshForADisabledAccount() {
            when(refreshTokens.rotate("token")).thenReturn(Optional.of(USER_ID));
            when(users.findById(USER_ID)).thenReturn(Optional.of(account(true, UserStatus.DISABLED)));

            assertThatThrownBy(() -> auth.refresh("token"))
                    .isInstanceOf(ApiException.class)
                    .extracting(exception -> ((ApiException) exception).getCode())
                    .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        }

        @Test
        void refusesToRefreshWhenTheAccountIsGone() {
            when(refreshTokens.rotate("token")).thenReturn(Optional.of(USER_ID));
            when(users.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auth.refresh("token")).isInstanceOf(ApiException.class);
        }

        @Test
        void denylistsTheAccessTokenForItsRemainingLifetimeOnLogout() {
            Instant expiry = Instant.now().plusSeconds(600);

            auth.logout("jti-123", expiry, "refresh-token");

            ArgumentCaptor<Duration> ttl = ArgumentCaptor.captor();
            verify(denylist).revoke(eq("jti-123"), ttl.capture());
            assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(590), Duration.ofSeconds(600));
            verify(refreshTokens).revoke("refresh-token");
        }

        @Test
        void logsOutCleanlyWithoutARefreshToken() {
            auth.logout("jti-123", Instant.now().plusSeconds(600), null);

            verify(refreshTokens).revoke(null);
            verify(audit).record(eq(AuditAction.LOGOUT), eq(AuditStatus.SUCCESS), eq("SESSION"),
                    eq("jti-123"), eq(null));
        }

        @Test
        void survivesALogoutCallWithNoTokenDetails() {
            auth.logout(null, null, "refresh-token");

            verify(denylist, never()).revoke(anyString(), any());
        }
    }

    @Test
    void registrationIsDelegatedToTheUserModule() {
        RegisterUserCommand command = new RegisterUserCommand("Farhan", "farhan", "f@example.com", PASSWORD);
        when(users.register(command)).thenReturn(account(true, UserStatus.ACTIVE));

        assertThat(auth.register(command).username()).isEqualTo("farhan");
        verify(users).register(command);
    }
}
