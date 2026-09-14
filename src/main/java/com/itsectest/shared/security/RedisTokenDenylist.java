package com.itsectest.shared.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisTokenDenylist implements TokenDenylist {

    private static final String KEY_PREFIX = "auth:denylist:";

    private final StringRedisTemplate redis;

    @Override
    public void revoke(String tokenId, Duration ttl) {
        if (tokenId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + tokenId, "1", ttl);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return tokenId != null && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + tokenId));
    }
}
