package com.itsectest.shared.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rl:";

    private static final String LUA = """
            local hits = redis.call('INCR', KEYS[1])
            if hits == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return { hits, redis.call('PTTL', KEYS[1]) }
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA, List.class);

    private final StringRedisTemplate redis;

    @Override
    public RateLimitVerdict check(String key, int limit, Duration window) {
        try {
            List<?> raw = redis.execute(SCRIPT, List.of(KEY_PREFIX + key),
                    String.valueOf(window.toMillis()));
            long hits = asLong(raw, 0, 1);
            long ttlMillis = asLong(raw, 1, window.toMillis());
            Duration retryAfter = Duration.ofMillis(Math.max(ttlMillis, 0));

            return hits > limit
                    ? RateLimitVerdict.denied(limit, retryAfter)
                    : RateLimitVerdict.allowed(limit, limit - hits, retryAfter);
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request for key {}", key, ex);
            return RateLimitVerdict.allowed(limit, limit, window);
        }
    }

    private static long asLong(List<?> values, int index, long fallback) {
        if (values == null || values.size() <= index || !(values.get(index) instanceof Number number)) {
            return fallback;
        }
        return number.longValue();
    }
}
