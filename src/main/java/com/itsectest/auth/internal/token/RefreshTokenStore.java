package com.itsectest.auth.internal.token;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.itsectest.shared.security.JwtProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redis;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public String issue(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + token, userId.toString(), properties.refreshTokenTtl());
        return token;
    }

    public Optional<UUID> rotate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + token);
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(KEY_PREFIX + token);
        }
    }
}
