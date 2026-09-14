package com.itsectest.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock private StringRedisTemplate redis;

    private RedisRateLimiter limiter;

    private void redisReturns(Object... values) {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), anyString())).thenReturn(List.of(values));
    }

    @Test
    void allowsARequestInsideTheQuota() {
        redisReturns(3L, 45_000L);
        limiter = new RedisRateLimiter(redis);

        RateLimitVerdict verdict = limiter.check("key", 5, WINDOW);

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.remaining()).isEqualTo(2);
        assertThat(verdict.retryAfter()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void deniesTheRequestPastTheQuota() {
        redisReturns(6L, 30_000L);
        limiter = new RedisRateLimiter(redis);

        RateLimitVerdict verdict = limiter.check("key", 5, WINDOW);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.remaining()).isZero();
        assertThat(verdict.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void allowsExactlyTheLimitAndNotOneMore() {
        redisReturns(5L, 10_000L);
        limiter = new RedisRateLimiter(redis);

        assertThat(limiter.check("key", 5, WINDOW).allowed()).isTrue();
    }

    @Test
    void failsOpenWhenRedisIsUnreachable() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        limiter = new RedisRateLimiter(redis);

        RateLimitVerdict verdict = limiter.check("key", 5, WINDOW);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.remaining()).isEqualTo(5);
    }

    @Test
    void copesWithAnUnexpectedScriptResult() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), anyString())).thenReturn(null);
        limiter = new RedisRateLimiter(redis);

        assertThat(limiter.check("key", 5, WINDOW).allowed()).isTrue();
    }

    @Test
    void neverReportsNegativeRemainingCapacity() {
        assertThat(RateLimitVerdict.allowed(5, -3, WINDOW).remaining()).isZero();
        assertThat(RateLimitVerdict.denied(5, WINDOW).allowed()).isFalse();
    }
}
