package com.itsectest.auth.internal.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itsectest.shared.error.OtpException;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String CHALLENGE_ID = "challenge-1";

    private final MfaProperties properties = new MfaProperties(
            true, 6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60));

    @Mock
    private OtpChannel channel;

    @Mock
    private OtpChallengeStore challenges;

    @Captor
    private ArgumentCaptor<String> codeCaptor;

    private OtpService otp;

    private static UserAccount account() {
        return new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                Role.VIEWER, UserStatus.ACTIVE, true, null);
    }

    @BeforeEach
    void setUp() {
        otp = new OtpService(properties, channel, challenges);
    }

    @Test
    void issuesASixDigitCodeAndSendsIt() {
        OtpIssued issued = otp.issue(account());

        verify(channel).deliver(any(), codeCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(codeCaptor.getValue()).hasSize(6).containsOnlyDigits();
        assertThat(issued.expiresIn()).isEqualTo(300);
        assertThat(issued.challengeId()).isNotBlank();
    }

    @Test
    void storesOnlyAHashOfTheCode() {
        otp.issue(account());

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(challenges).create(anyString(), eq(USER_ID), stored.capture(), any());

        verify(channel).deliver(any(), codeCaptor.capture(), any());
        assertThat(stored.getValue()).isNotEqualTo(codeCaptor.getValue()).hasSize(64);
    }

    @Test
    void masksTheDestinationAddress() {
        assertThat(otp.issue(account()).maskedEmail()).isEqualTo("f****n@example.com");
    }

    @Test
    void acceptsTheCorrectCode() {
        String code = "482913";
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, code), 0)));

        assertThat(otp.verify(CHALLENGE_ID, code)).isEqualTo(USER_ID);
        verify(challenges).delete(CHALLENGE_ID);
    }

    @Test
    void toleratesSurroundingWhitespaceInThePastedCode() {
        String code = "482913";
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, code), 0)));

        assertThat(otp.verify(CHALLENGE_ID, "  482913 ")).isEqualTo(USER_ID);
    }

    @Test
    void countsAWrongCodeAndSaysHowManyAttemptsRemain() {
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, "111111"), 0)));
        when(challenges.recordWrongAttempt(CHALLENGE_ID)).thenReturn(1);

        assertThatThrownBy(() -> otp.verify(CHALLENGE_ID, "222222"))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("2 attempt(s) remaining");
        verify(challenges, never()).delete(CHALLENGE_ID);
    }

    @Test
    void killsTheChallengeOnTheLastWrongAttempt() {
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, "111111"), 2)));
        when(challenges.recordWrongAttempt(CHALLENGE_ID)).thenReturn(3);

        assertThatThrownBy(() -> otp.verify(CHALLENGE_ID, "222222"))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Too many incorrect codes");
        verify(challenges).delete(CHALLENGE_ID);
    }

    @Test
    void refusesAChallengeThatIsAlreadyExhausted() {
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, "111111"), 3)));

        assertThatThrownBy(() -> otp.verify(CHALLENGE_ID, "111111"))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Too many incorrect codes");
    }

    @Test
    void refusesAnExpiredOrUnknownChallenge() {
        when(challenges.find("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otp.verify("gone", "111111"))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void treatsAMissingCodeAsWrong() {
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, OtpService.hash(CHALLENGE_ID, "111111"), 0)));
        when(challenges.recordWrongAttempt(CHALLENGE_ID)).thenReturn(1);

        assertThatThrownBy(() -> otp.verify(CHALLENGE_ID, null)).isInstanceOf(OtpException.class);
    }

    @Test
    void resendsAFreshCodeWhenTheCooldownAllowsIt() {
        when(challenges.claimResendSlot(CHALLENGE_ID, Duration.ofSeconds(60))).thenReturn(true);

        otp.resend(CHALLENGE_ID, account());

        verify(challenges).replaceCode(eq(CHALLENGE_ID), anyString(), eq(Duration.ofMinutes(5)));
        verify(channel, times(1)).deliver(any(), anyString(), any());
    }

    @Test
    void refusesToResendDuringTheCooldown() {
        when(challenges.claimResendSlot(CHALLENGE_ID, Duration.ofSeconds(60))).thenReturn(false);

        assertThatThrownBy(() -> otp.resend(CHALLENGE_ID, account()))
                .isInstanceOf(OtpException.class)
                .hasMessageContaining("Please wait");
        verify(channel, never()).deliver(any(), anyString(), any());
    }

    @Test
    void resolvesTheOwnerOfAChallenge() {
        when(challenges.find(CHALLENGE_ID)).thenReturn(Optional.of(
                new OtpChallenge(CHALLENGE_ID, USER_ID, "hash", 0)));

        assertThat(otp.ownerOf(CHALLENGE_ID)).isEqualTo(USER_ID);
    }

    @Test
    void reportsTheDeliveryChannel() {
        when(channel.name()).thenReturn("EMAIL");

        assertThat(otp.channelName()).isEqualTo("EMAIL");
    }

    @Test
    void saltsTheHashWithTheChallengeId() {
        assertThat(OtpService.hash("challenge-a", "123456"))
                .isNotEqualTo(OtpService.hash("challenge-b", "123456"));
    }

    @Test
    void masksShortLocalPartsWithoutCrashing() {
        assertThat(OtpService.maskEmail("a@example.com")).isEqualTo("***@example.com");
        assertThat(OtpService.maskEmail("ab@example.com")).isEqualTo("a*b@example.com");
    }

    @Test
    void issuesChallengesWithDistinctIdentifiers() {
        assertThat(otp.issue(account()).challengeId())
                .isNotEqualTo(otp.issue(account()).challengeId());
    }

    @Test
    void usesTheConfiguredCodeLength() {
        OtpService eightDigits = new OtpService(
                new MfaProperties(true, 8, Duration.ofMinutes(5), 3, Duration.ofSeconds(60)),
                channel, challenges);

        eightDigits.issue(account());

        verify(channel).deliver(any(), codeCaptor.capture(), any());
        assertThat(codeCaptor.getValue()).hasSize(8);
    }

    @Test
    void fallsBackToSaneDefaultsForNonsenseConfiguration() {
        MfaProperties defaults = new MfaProperties(true, 2, null, 0, null);

        assertThat(defaults.otpLength()).isEqualTo(6);
        assertThat(defaults.otpTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.maxVerifyAttempts()).isEqualTo(3);
        assertThat(defaults.resendCooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(Instant.now()).isNotNull();
    }
}
