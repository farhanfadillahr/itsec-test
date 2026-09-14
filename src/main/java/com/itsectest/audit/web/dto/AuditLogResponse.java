package com.itsectest.audit.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuditLogResponse", description = "One entry in the audit trail")
public record AuditLogResponse(
        UUID id,
        UUID actorId,
        @Schema(example = "farhan") String actorUsername,
        AuditAction action,
        AuditStatus status,
        @Schema(example = "ARTICLE") String resourceType,
        String resourceId,
        @Schema(example = "POST") String httpMethod,
        @Schema(example = "/api/v1/articles") String endpoint,
        @Schema(example = "203.0.113.42") String ipAddress,
        Device device,
        @Schema(description = "Action-specific extras") Map<String, Object> detail,
        Instant timestamp) {

    @Schema(name = "Device", description = "Parsed from the User-Agent header")
    public record Device(
            @Schema(example = "Chrome") String browser,
            @Schema(example = "141.0.0.0") String browserVersion,
            @Schema(example = "macOS 15.3") String os,
            @Schema(example = "DESKTOP") String type,
            @Schema(description = "The raw header, kept verbatim") String userAgent) {
    }

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorUsername(),
                log.getAction(),
                log.getStatus(),
                log.getResourceType(),
                log.getResourceId(),
                log.getHttpMethod(),
                log.getEndpoint(),
                log.getIpAddress(),
                new Device(log.getBrowser(), log.getBrowserVersion(), log.getOs(),
                        log.getDeviceType(), log.getUserAgent()),
                log.getDetail(),
                log.getCreatedAt());
    }
}
