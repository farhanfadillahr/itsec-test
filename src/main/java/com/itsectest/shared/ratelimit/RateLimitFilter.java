package com.itsectest.shared.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.audit.api.RequestMetadata;
import com.itsectest.shared.error.ApiError;
import com.itsectest.shared.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final AuditPublisher auditPublisher;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        RateLimitProperties.Rule rule = matchingRule(request).orElse(null);
        int limit = rule == null ? properties.defaultLimit() : rule.limit();
        Duration window = rule == null ? properties.defaultWindow() : rule.window();
        String bucket = (rule == null ? "default" : rule.pattern()) + ":"
                + RequestMetadata.clientIp(request);

        RateLimitVerdict verdict = rateLimiter.check(bucket, limit, window);
        response.setHeader("X-RateLimit-Limit", String.valueOf(verdict.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(verdict.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(verdict.retryAfter().toSeconds()));

        if (verdict.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        reject(request, response, verdict);
    }

    private Optional<RateLimitProperties.Rule> matchingRule(HttpServletRequest request) {
        return properties.rules().stream()
                .filter(rule -> rule.appliesTo(request.getMethod()))
                .filter(rule -> PATHS.match(rule.pattern(), request.getRequestURI()))
                .findFirst();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RateLimitVerdict verdict)
            throws IOException {

        auditPublisher.record(AuditAction.RATE_LIMIT_EXCEEDED, AuditStatus.FAILURE, "ENDPOINT",
                request.getRequestURI(), Map.of("retryAfterSeconds", verdict.retryAfter().toSeconds()));

        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(verdict.retryAfter().toSeconds()));
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(
                ErrorCode.RATE_LIMITED,
                "Too many requests. Try again in " + verdict.retryAfter().toSeconds() + " seconds.",
                request.getRequestURI())));
    }
}
