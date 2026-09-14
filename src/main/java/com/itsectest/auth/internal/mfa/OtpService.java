package com.itsectest.auth.internal.mfa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.itsectest.shared.error.OtpException;
import com.itsectest.user.api.UserAccount;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final MfaProperties properties;
    private final OtpChannel channel;
    private final OtpChallengeStore challenges;
    private final SecureRandom random = new SecureRandom();

    public OtpIssued issue(UserAccount account) {
        String code = generateCode();
        String challengeId = UUID.randomUUID().toString();
        challenges.create(challengeId, account.id(), hash(challengeId, code), properties.otpTtl());

        channel.deliver(account, code, properties.otpTtl());
        return new OtpIssued(challengeId, maskEmail(account.email()), properties.otpTtl().toSeconds());
    }

    public UUID verify(String challengeId, String code) {
        OtpChallenge challenge = challenges.find(challengeId)
                .orElseThrow(() -> new OtpException("The verification code has expired. Please sign in again."));

        if (challenge.attempts() >= properties.maxVerifyAttempts()) {
            challenges.delete(challengeId);
            throw new OtpException("Too many incorrect codes. Please sign in again.");
        }
        if (!matches(challenge, code)) {
            int used = challenges.recordWrongAttempt(challengeId);
            int left = Math.max(properties.maxVerifyAttempts() - used, 0);
            if (left == 0) {
                challenges.delete(challengeId);
                throw new OtpException("Too many incorrect codes. Please sign in again.");
            }
            throw new OtpException("Incorrect code. " + left + " attempt(s) remaining.");
        }

        challenges.delete(challengeId);
        return challenge.userId();
    }

    public OtpIssued resend(String challengeId, UserAccount account) {
        if (!challenges.claimResendSlot(challengeId, properties.resendCooldown())) {
            throw new OtpException("A code was just sent. Please wait before requesting another.");
        }
        String code = generateCode();
        challenges.replaceCode(challengeId, hash(challengeId, code), properties.otpTtl());
        channel.deliver(account, code, properties.otpTtl());
        return new OtpIssued(challengeId, maskEmail(account.email()), properties.otpTtl().toSeconds());
    }

    public UUID ownerOf(String challengeId) {
        return challenges.find(challengeId)
                .orElseThrow(() -> new OtpException("The verification code has expired. Please sign in again."))
                .userId();
    }

    public String channelName() {
        return channel.name();
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.otpLength());
        return String.format("%0" + properties.otpLength() + "d", random.nextInt(bound));
    }

    private boolean matches(OtpChallenge challenge, String code) {
        if (code == null) {
            return false;
        }
        return MessageDigest.isEqual(
                challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                hash(challenge.id(), code.trim()).getBytes(StandardCharsets.UTF_8));
    }

    static String hash(String challengeId, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        String local = email.substring(0, at);
        String masked = local.charAt(0) + "*".repeat(Math.max(local.length() - 2, 1))
                + (local.length() > 1 ? String.valueOf(local.charAt(local.length() - 1)) : "");
        return masked + email.substring(at);
    }
}
