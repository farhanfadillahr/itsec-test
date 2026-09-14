package com.itsectest.shared.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int defaultLimit,
        Duration defaultWindow,
        List<Rule> rules) {

    public RateLimitProperties {
        if (defaultLimit <= 0) {
            defaultLimit = 100;
        }
        if (defaultWindow == null) {
            defaultWindow = Duration.ofMinutes(1);
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(String pattern, List<String> methods, int limit, Duration window) {

        public Rule {
            methods = methods == null ? List.of() : methods.stream().map(String::toUpperCase).toList();
        }

        public boolean appliesTo(String method) {
            return methods.isEmpty() || methods.contains(method.toUpperCase());
        }
    }
}
