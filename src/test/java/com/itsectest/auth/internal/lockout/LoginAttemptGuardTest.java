package com.itsectest.auth.internal.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptGuardTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FAILURE_KEY = "login:fail:" + USER_ID;
    private static final String LOCK_KEY = "login:lock:" + USER_ID;

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private LoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        guard = new LoginAttemptGuard(redis,
                new LockoutProperties(5, Duration.ofMinutes(10), Duration.ofMinutes(30)));
    }

    @Test
    void startsTheTenMinuteWindowOnTheFirstFailure() {
        when(valueOps.increment(FAILURE_KEY)).thenReturn(1L);

        LockoutState state = guard.recordFailure(USER_ID);

        assertThat(state.failedAttempts()).isEqualTo(1);
        assertThat(state.remainingAttempts()).isEqualTo(4);
        assertThat(state.justLocked()).isFalse();
        verify(redis).expire(FAILURE_KEY, Duration.ofMinutes(10));
    }

    @Test
    void doesNotRestartTheWindowOnLaterFailures() {
        when(valueOps.increment(FAILURE_KEY)).thenReturn(3L);

        assertThat(guard.recordFailure(USER_ID).remainingAttempts()).isEqualTo(2);
        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void locksForThirtyMinutesOnTheFifthFailure() {
        when(valueOps.increment(FAILURE_KEY)).thenReturn(5L);

        LockoutState state = guard.recordFailure(USER_ID);

        assertThat(state.justLocked()).isTrue();
        assertThat(state.remainingAttempts()).isZero();
        assertThat(state.lockedUntil()).isNotNull();
        verify(valueOps).set(LOCK_KEY, "1", Duration.ofMinutes(30));
        verify(redis).delete(FAILURE_KEY);
    }

    @Test
    void copesWithAnIncrementThatReturnsNothing() {
        when(valueOps.increment(FAILURE_KEY)).thenReturn(null);

        assertThat(guard.recordFailure(USER_ID).failedAttempts()).isEqualTo(1);
    }

    @Test
    void reportsHowLongALockHasLeft() {
        when(redis.getExpire(LOCK_KEY)).thenReturn(720L);

        assertThat(guard.lockRemaining(USER_ID)).contains(Duration.ofMinutes(12));
    }

    @Test
    void reportsNoLockWhenThereIsNone() {
        when(redis.getExpire(LOCK_KEY)).thenReturn(-2L);
        assertThat(guard.lockRemaining(USER_ID)).isEmpty();

        when(redis.getExpire(LOCK_KEY)).thenReturn(null);
        assertThat(guard.lockRemaining(USER_ID)).isEmpty();
    }

    @Test
    void aSuccessfulLoginClearsBothTheCounterAndTheLock() {
        guard.reset(USER_ID);

        verify(redis).delete(FAILURE_KEY);
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    void exposesItsConfiguredThresholds() {
        assertThat(guard.maxAttempts()).isEqualTo(5);
        assertThat(guard.lockoutDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void fallsBackToTheRequiredDefaultsIfMisconfigured() {
        LockoutProperties defaults = new LockoutProperties(0, null, null);

        assertThat(defaults.maxAttempts()).isEqualTo(5);
        assertThat(defaults.window()).isEqualTo(Duration.ofMinutes(10));
        assertThat(defaults.duration()).isEqualTo(Duration.ofMinutes(30));
    }
}
