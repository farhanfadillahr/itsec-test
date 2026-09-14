package com.itsectest.auth.internal.lockout;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.itsectest.user.api.UserUnlocked;

class UserUnlockedListenerTest {

    @Test
    void anAdminUnlockClearsTheFailureCounterAndTheLock() {
        LoginAttemptGuard guard = mock(LoginAttemptGuard.class);
        UUID userId = UUID.randomUUID();

        new UserUnlockedListener(guard).on(new UserUnlocked(userId));

        verify(guard).reset(userId);
    }
}
