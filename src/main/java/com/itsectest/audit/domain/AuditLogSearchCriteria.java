package com.itsectest.audit.domain;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditStatus;

public record AuditLogSearchCriteria(
        UUID actorId,
        String actorUsername,
        AuditAction action,
        AuditStatus status,
        String resourceType,
        String resourceId,
        Instant from,
        Instant to) {
}
