package com.itsectest.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.itsectest.shared.audit.api.RequestMetadata;

class RequestMetadataTest {

    @Test
    void capturesMethodPathAgentAndAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/articles");
        request.addHeader("User-Agent", "curl/8.7.1");
        request.setRemoteAddr("198.51.100.7");

        RequestMetadata metadata = RequestMetadata.from(request);

        assertThat(metadata.httpMethod()).isEqualTo("POST");
        assertThat(metadata.endpoint()).isEqualTo("/api/v1/articles");
        assertThat(metadata.userAgent()).isEqualTo("curl/8.7.1");
        assertThat(metadata.ipAddress()).isEqualTo("198.51.100.7");
    }

    @Test
    void prefersTheFirstHopOfXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.1");

        assertThat(RequestMetadata.from(request).ipAddress()).isEqualTo("203.0.113.42");
    }

    @Test
    void fallsBackToXRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertThat(RequestMetadata.from(request).ipAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresABlankForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("10.0.0.1");

        assertThat(RequestMetadata.from(request).ipAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void hasAnEmptyFormForEventsRaisedOutsideARequest() {
        assertThat(RequestMetadata.from(null)).isEqualTo(RequestMetadata.empty());
        assertThat(RequestMetadata.empty().endpoint()).isNull();
    }
}
