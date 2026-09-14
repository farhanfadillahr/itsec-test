package com.itsectest.auth.internal.lockout;

import java.time.Instant;

public record LockoutState(int failedAttempts, int remainingAttempts, Instant lockedUntil) {

    public boolean justLocked() {
        return lockedUntil != null;
    }
}
