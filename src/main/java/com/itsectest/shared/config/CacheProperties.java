package com.itsectest.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(Duration articleTtl) {

    public CacheProperties {
        if (articleTtl == null) {
            articleTtl = Duration.ofMinutes(10);
        }
    }
}
