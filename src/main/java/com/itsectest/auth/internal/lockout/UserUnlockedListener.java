package com.itsectest.auth.internal.lockout;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.itsectest.user.api.UserUnlocked;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserUnlockedListener {

    private final LoginAttemptGuard guard;

    @TransactionalEventListener(fallbackExecution = true)
    public void on(UserUnlocked event) {
        guard.reset(event.userId());
    }
}
