package com.itsectest.auth.internal.mfa;

import java.util.UUID;

public record OtpChallenge(String id, UUID userId, String codeHash, int attempts) {
}
