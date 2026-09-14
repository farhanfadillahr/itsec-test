package com.itsectest.shared.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

@Builder
public record AuditEvent(
        UUID actorId,
        String actorUsername,
        AuditAction action,
        String resourceType,
        String resourceId,
        AuditStatus status,
        Map<String, Object> detail,
        RequestMetadata request,
        Instant occurredAt) {

    public AuditEvent {
        if (status == null) {
            status = AuditStatus.SUCCESS;
        }
        if (request == null) {
            request = RequestMetadata.empty();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
