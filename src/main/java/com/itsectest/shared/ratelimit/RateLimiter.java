package com.itsectest.shared.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    RateLimitVerdict check(String key, int limit, Duration window);
}
