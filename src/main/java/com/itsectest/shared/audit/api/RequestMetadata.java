package com.itsectest.shared.audit.api;

import jakarta.servlet.http.HttpServletRequest;

public record RequestMetadata(String ipAddress, String userAgent, String httpMethod, String endpoint) {

    private static final RequestMetadata EMPTY = new RequestMetadata(null, null, null, null);

    public static RequestMetadata empty() {
        return EMPTY;
    }

    public static RequestMetadata from(HttpServletRequest request) {
        if (request == null) {
            return EMPTY;
        }
        return new RequestMetadata(
                clientIp(request),
                request.getHeader("User-Agent"),
                request.getMethod(),
                request.getRequestURI());
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return (realIp != null && !realIp.isBlank()) ? realIp.trim() : request.getRemoteAddr();
    }
}
