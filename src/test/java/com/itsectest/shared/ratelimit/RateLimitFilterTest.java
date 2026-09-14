package com.itsectest.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private AuditPublisher auditPublisher;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    private void configure(boolean enabled) {
        properties = new RateLimitProperties(enabled, 100, Duration.ofMinutes(1), List.of(
                new RateLimitProperties.Rule("/api/v1/auth/login", List.of("POST"), 5, Duration.ofMinutes(1))));
        filter = new RateLimitFilter(rateLimiter, properties, auditPublisher, JsonMapper.builder().build());
    }

    @BeforeEach
    void setUp() {
        configure(true);
    }

    @Test
    void letsAnAllowedRequestThroughAndAdvertisesTheQuota() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.allowed(5, 4, Duration.ofSeconds(60)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void answersWithTooManyRequestsWhenTheQuotaIsSpent() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.denied(5, Duration.ofSeconds(42)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED").contains("42 seconds");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void recordsARateLimitTripInTheAuditTrail() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.denied(5, Duration.ofSeconds(42)));

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(), new MockFilterChain());

        verify(auditPublisher).record(eq(AuditAction.RATE_LIMIT_EXCEEDED), eq(AuditStatus.FAILURE),
                eq("ENDPOINT"), eq("/api/v1/auth/login"), any());
    }

    @Test
    void appliesTheSpecificRuleAheadOfTheDefault() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.allowed(5, 4, Duration.ofSeconds(60)));

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<Integer> limit = ArgumentCaptor.captor();
        verify(rateLimiter).check(anyString(), limit.capture(), any());
        assertThat(limit.getValue()).isEqualTo(5);
    }

    @Test
    void fallsBackToTheDefaultQuotaForOtherEndpoints() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.allowed(100, 99, Duration.ofSeconds(60)));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/articles"),
                new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<Integer> limit = ArgumentCaptor.captor();
        verify(rateLimiter).check(anyString(), limit.capture(), any());
        assertThat(limit.getValue()).isEqualTo(100);
    }

    @Test
    void ignoresTheRuleWhenTheMethodDoesNotMatch() {
        MockHttpServletRequest getLogin = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        assertThat(properties.rules().getFirst().appliesTo("GET")).isFalse();
        assertThat(properties.rules().getFirst().appliesTo("post")).isTrue();
        assertThat(getLogin.getMethod()).isEqualTo("GET");
    }

    @Test
    void skipsNonApiTrafficAndStaysOutOfTheWayWhenDisabled() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui.html"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/articles"))).isFalse();

        configure(false);
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/articles"))).isTrue();
    }

    @Test
    void keysTheBucketByClientAddressSoOneCallerCannotStarveAnother() throws Exception {
        when(rateLimiter.check(anyString(), anyInt(), any()))
                .thenReturn(RateLimitVerdict.allowed(5, 4, Duration.ofSeconds(60)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> bucket = ArgumentCaptor.captor();
        verify(rateLimiter).check(bucket.capture(), anyInt(), any());
        assertThat(bucket.getValue()).contains("203.0.113.42");
    }

    @Test
    void suppliesDefaultsForAnEmptyConfiguration() {
        RateLimitProperties defaults = new RateLimitProperties(true, 0, null, null);

        assertThat(defaults.defaultLimit()).isEqualTo(100);
        assertThat(defaults.defaultWindow()).isEqualTo(Duration.ofMinutes(1));
        assertThat(defaults.rules()).isEmpty();
        verify(rateLimiter, never()).check(anyString(), anyInt(), any());
    }
}
