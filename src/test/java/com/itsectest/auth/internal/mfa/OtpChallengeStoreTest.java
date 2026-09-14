package com.itsectest.auth.internal.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpChallengeStoreTest {

    private static final String ID = "challenge-1";
    private static final String KEY = "mfa:otp:" + ID;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;

    private OtpChallengeStore store;

    @BeforeEach
    void setUp() {
        doReturn(hashOps).when(redis).opsForHash();
        when(redis.opsForValue()).thenReturn(valueOps);
        store = new OtpChallengeStore(redis);
    }

    @Test
    void storesTheChallengeUnderTheConfiguredExpiry() {
        store.create(ID, USER_ID, "hash", Duration.ofMinutes(5));

        verify(hashOps).putAll(eq(KEY), any());
        verify(redis).expire(KEY, Duration.ofMinutes(5));
    }

    @Test
    void readsBackAStoredChallenge() {
        when(hashOps.entries(KEY)).thenReturn(Map.of(
                "userId", USER_ID.toString(), "codeHash", "hash", "attempts", "2"));

        assertThat(store.find(ID)).hasValueSatisfying(challenge -> {
            assertThat(challenge.id()).isEqualTo(ID);
            assertThat(challenge.userId()).isEqualTo(USER_ID);
            assertThat(challenge.codeHash()).isEqualTo("hash");
            assertThat(challenge.attempts()).isEqualTo(2);
        });
    }

    @Test
    void reportsAnExpiredChallengeAsAbsent() {
        when(hashOps.entries(KEY)).thenReturn(Map.of());

        assertThat(store.find(ID)).isEmpty();
        assertThat(store.find(null)).isEmpty();
        assertThat(store.find("  ")).isEmpty();
    }

    @Test
    void countsWrongAttempts() {
        when(hashOps.increment(KEY, "attempts", 1)).thenReturn(3L);

        assertThat(store.recordWrongAttempt(ID)).isEqualTo(3);
    }

    @Test
    void copesWithAnIncrementThatReturnsNothing() {
        when(hashOps.increment(KEY, "attempts", 1)).thenReturn(null);

        assertThat(store.recordWrongAttempt(ID)).isEqualTo(1);
    }

    @Test
    void replacingTheCodeResetsTheAttemptCounterAndTheTtl() {
        store.replaceCode(ID, "new-hash", Duration.ofMinutes(5));

        verify(hashOps).put(KEY, "codeHash", "new-hash");
        verify(hashOps).put(KEY, "attempts", "0");
        verify(redis).expire(KEY, Duration.ofMinutes(5));
    }

    @Test
    void deletesAChallengeAndReportsItsRemainingLife() {
        when(redis.getExpire(KEY)).thenReturn(120L);

        store.delete(ID);

        verify(redis).delete(KEY);
        assertThat(store.remainingTtl(ID)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void reportsNoRemainingLifeForAKeyWithoutOne() {
        when(redis.getExpire(KEY)).thenReturn(-2L);
        assertThat(store.remainingTtl(ID)).isZero();

        when(redis.getExpire(KEY)).thenReturn(null);
        assertThat(store.remainingTtl(ID)).isZero();
    }

    @Test
    void grantsOneResendSlotPerCooldown() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true, false);

        assertThat(store.claimResendSlot(ID, Duration.ofSeconds(60))).isTrue();
        assertThat(store.claimResendSlot(ID, Duration.ofSeconds(60))).isFalse();
    }
}
