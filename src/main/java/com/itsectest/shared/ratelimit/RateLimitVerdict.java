package com.itsectest.shared.ratelimit;

import java.time.Duration;

public record RateLimitVerdict(boolean allowed, long limit, long remaining, Duration retryAfter) {

    public static RateLimitVerdict allowed(long limit, long remaining, Duration retryAfter) {
        return new RateLimitVerdict(true, limit, Math.max(remaining, 0), retryAfter);
    }

    public static RateLimitVerdict denied(long limit, Duration retryAfter) {
        return new RateLimitVerdict(false, limit, 0, retryAfter);
    }
}
