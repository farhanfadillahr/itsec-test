package com.itsectest.audit.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogRepository;
import com.itsectest.audit.domain.AuditLogSearchCriteria;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.Auditable;
import com.itsectest.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogs;

    @Auditable(action = AuditAction.AUDIT_LOG_VIEWED, resourceType = "AUDIT_LOG")
    public Page<AuditLog> search(AuditLogSearchCriteria criteria, Pageable pageable) {
        return auditLogs.search(criteria, pageable);
    }

    @Auditable(action = AuditAction.AUDIT_LOG_VIEWED, resourceType = "AUDIT_LOG", resourceId = "#id")
    public AuditLog get(UUID id) {
        return auditLogs.findById(id).orElseThrow(() -> new NotFoundException("Audit log entry not found"));
    }
}
