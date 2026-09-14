package com.itsectest.auth.internal.mfa;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OtpChallengeStore {

    private static final String CHALLENGE_KEY = "mfa:otp:";
    private static final String COOLDOWN_KEY = "mfa:cooldown:";
    private static final String FIELD_USER = "userId";
    private static final String FIELD_HASH = "codeHash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;

    public void create(String challengeId, UUID userId, String codeHash, Duration ttl) {
        redis.opsForHash().putAll(CHALLENGE_KEY + challengeId, Map.of(
                FIELD_USER, userId.toString(),
                FIELD_HASH, codeHash,
                FIELD_ATTEMPTS, "0"));
        redis.expire(CHALLENGE_KEY + challengeId, ttl);
    }

    public Optional<OtpChallenge> find(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> stored = redis.opsForHash().entries(CHALLENGE_KEY + challengeId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OtpChallenge(
                challengeId,
                UUID.fromString(String.valueOf(stored.get(FIELD_USER))),
                String.valueOf(stored.get(FIELD_HASH)),
                Integer.parseInt(String.valueOf(stored.get(FIELD_ATTEMPTS)))));
    }

    public int recordWrongAttempt(String challengeId) {
        Long attempts = redis.opsForHash().increment(CHALLENGE_KEY + challengeId, FIELD_ATTEMPTS, 1);
        return attempts == null ? 1 : attempts.intValue();
    }

    public void replaceCode(String challengeId, String codeHash, Duration ttl) {
        redis.opsForHash().put(CHALLENGE_KEY + challengeId, FIELD_HASH, codeHash);
        redis.opsForHash().put(CHALLENGE_KEY + challengeId, FIELD_ATTEMPTS, "0");
        redis.expire(CHALLENGE_KEY + challengeId, ttl);
    }

    public void delete(String challengeId) {
        redis.delete(CHALLENGE_KEY + challengeId);
    }

    public Duration remainingTtl(String challengeId) {
        Long seconds = redis.getExpire(CHALLENGE_KEY + challengeId);
        return Duration.ofSeconds(seconds == null || seconds < 0 ? 0 : seconds);
    }

    public boolean claimResendSlot(String challengeId, Duration cooldown) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(COOLDOWN_KEY + challengeId, "1", cooldown));
    }
}
