package com.itsectest.auth.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

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
import com.itsectest.user.api.RegisterUserCommand;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;
import com.itsectest.user.domain.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    // same message for an unknown user and a wrong password
    private static final String CREDENTIALS_REJECTED = "Username, email or password is incorrect";

    private final UserFacade users;
    private final IdentifierResolver identifiers;
    private final LoginAttemptGuard lockoutGuard;
    private final OtpService otp;
    private final TokenFactory tokens;
    private final RefreshTokenStore refreshTokens;
    private final MfaProperties mfaProperties;
    private final AuditPublisher audit;
    private final com.itsectest.shared.security.TokenDenylist denylist;

    public UserAccount register(RegisterUserCommand command) {
        return users.register(command);
    }

    public LoginOutcome login(String identifier, String rawPassword) {
        UserAccount account = identifiers.resolve(identifier).orElse(null);
        if (account == null) {
            audit.recordForSubject(null, identifier, AuditAction.LOGIN_FAILED, AuditStatus.FAILURE,
                    detail("reason", "NO_SUCH_ACCOUNT", "identifierKind", identifiers.kindOf(identifier)));
            throw new InvalidCredentialsException(CREDENTIALS_REJECTED);
        }

        lockoutGuard.lockRemaining(account.id()).ifPresent(remaining -> {
            audit.recordForSubject(account.id(), account.username(), AuditAction.LOGIN_BLOCKED,
                    AuditStatus.FAILURE, detail("lockRemainingSeconds", remaining.toSeconds()));
            throw new AccountLockedException(lockedMessage(remaining));
        });

        if (account.status() == UserStatus.DISABLED) {
            audit.recordForSubject(account.id(), account.username(), AuditAction.LOGIN_BLOCKED,
                    AuditStatus.FAILURE, detail("reason", "ACCOUNT_DISABLED"));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled. Contact an administrator.");
        }

        if (!users.matchesPassword(account.id(), rawPassword)) {
            throw onWrongPassword(account);
        }

        lockoutGuard.reset(account.id());
        if (account.isLocked()) {
            users.clearLock(account.id());
        }

        return mfaProperties.enabled() && account.mfaEnabled()
                ? challenge(account)
                : authenticate(account, AuditAction.LOGIN_SUCCESS);
    }

    public IssuedTokens verifyMfa(String challengeId, String code) {
        UUID userId;
        try {
            userId = otp.verify(challengeId, code);
        } catch (OtpException failure) {
            audit.recordForSubject(ownerOrNull(challengeId), null, AuditAction.OTP_FAILED,
                    AuditStatus.FAILURE, detail("reason", failure.getMessage()));
            throw failure;
        }

        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException(CREDENTIALS_REJECTED));
        audit.recordForSubject(account.id(), account.username(), AuditAction.OTP_VERIFIED,
                AuditStatus.SUCCESS, detail("channel", otp.channelName()));

        return ((LoginOutcome.Authenticated) authenticate(account, AuditAction.LOGIN_SUCCESS)).tokens();
    }

    public OtpIssued resendOtp(String challengeId) {
        UUID userId = otp.ownerOf(challengeId);
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new OtpException("The verification code has expired. Please sign in again."));

        OtpIssued issued = otp.resend(challengeId, account);
        audit.recordForSubject(account.id(), account.username(), AuditAction.OTP_RESENT,
                AuditStatus.SUCCESS, detail("channel", otp.channelName()));
        return issued;
    }

    public IssuedTokens refresh(String refreshToken) {
        UUID userId = refreshTokens.rotate(refreshToken)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN,
                        "Refresh token is invalid, expired or already used"));

        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Refresh token is no longer valid"));
        if (account.status() == UserStatus.DISABLED) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled.");
        }

        IssuedTokens issued = tokens.issueFor(account);
        audit.recordForSubject(account.id(), account.username(), AuditAction.TOKEN_REFRESHED,
                AuditStatus.SUCCESS, null);
        return issued;
    }

    public void logout(String accessTokenId, Instant accessTokenExpiry, String refreshToken) {
        if (accessTokenId != null && accessTokenExpiry != null) {
            denylist.revoke(accessTokenId, Duration.between(Instant.now(), accessTokenExpiry));
        }
        refreshTokens.revoke(refreshToken);
        audit.record(AuditAction.LOGOUT, AuditStatus.SUCCESS, "SESSION", accessTokenId, null);
    }

    private RuntimeException onWrongPassword(UserAccount account) {
        LockoutState state = lockoutGuard.recordFailure(account.id());
        if (state.justLocked()) {
            users.lockUntil(account.id(), state.lockedUntil());
            audit.recordForSubject(account.id(), account.username(), AuditAction.ACCOUNT_LOCKED,
                    AuditStatus.FAILURE, Map.of(
                            "failedAttempts", state.failedAttempts(),
                            "lockedUntil", state.lockedUntil().toString()));
            return new AccountLockedException(lockedMessage(lockoutGuard.lockoutDuration()));
        }
        audit.recordForSubject(account.id(), account.username(), AuditAction.LOGIN_FAILED,
                AuditStatus.FAILURE, Map.of(
                        "reason", "WRONG_PASSWORD",
                        "remainingAttempts", state.remainingAttempts()));
        return new InvalidCredentialsException(CREDENTIALS_REJECTED);
    }

    private LoginOutcome challenge(UserAccount account) {
        OtpIssued issued = otp.issue(account);
        audit.recordForSubject(account.id(), account.username(), AuditAction.OTP_ISSUED,
                AuditStatus.SUCCESS, detail("channel", otp.channelName()));
        return new LoginOutcome.MfaChallenge(issued.challengeId(), issued.maskedEmail(), issued.expiresIn());
    }

    private LoginOutcome authenticate(UserAccount account, AuditAction action) {
        IssuedTokens issued = tokens.issueFor(account);
        audit.recordForSubject(account.id(), account.username(), action, AuditStatus.SUCCESS,
                detail("role", account.role().name()));
        return new LoginOutcome.Authenticated(issued);
    }

    private UUID ownerOrNull(String challengeId) {
        try {
            return otp.ownerOf(challengeId);
        } catch (OtpException ignored) {
            return null;
        }
    }

    private static Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detail = new HashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detail.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detail;
    }

    private static String lockedMessage(Duration remaining) {
        long minutes = Math.max(remaining.toMinutes(), 1);
        return "Too many failed sign-in attempts. This account is locked for another "
                + minutes + " minute(s).";
    }
}
