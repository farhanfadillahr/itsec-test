package com.itsectest.auth.internal.lockout;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private static final String FAILURE_KEY = "login:fail:";
    private static final String LOCK_KEY = "login:lock:";

    private final StringRedisTemplate redis;
    private final LockoutProperties properties;

    public Optional<Duration> lockRemaining(UUID userId) {
        Long seconds = redis.getExpire(LOCK_KEY + userId);
        return seconds != null && seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
    }

    public LockoutState recordFailure(UUID userId) {
        String key = FAILURE_KEY + userId;
        Long attempts = redis.opsForValue().increment(key);
        long failures = attempts == null ? 1 : attempts;

        if (failures == 1) {
            redis.expire(key, properties.window());
        }
        if (failures < properties.maxAttempts()) {
            return new LockoutState((int) failures, properties.maxAttempts() - (int) failures, null);
        }

        redis.opsForValue().set(LOCK_KEY + userId, "1", properties.duration());
        redis.delete(key);
        return new LockoutState((int) failures, 0, Instant.now().plus(properties.duration()));
    }

    public void reset(UUID userId) {
        redis.delete(FAILURE_KEY + userId);
        redis.delete(LOCK_KEY + userId);
    }

    public Duration lockoutDuration() {
        return properties.duration();
    }

    public int maxAttempts() {
        return properties.maxAttempts();
    }
}
