package com.itsectest.audit.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogRepository {

    AuditLog append(AuditLog entry);

    Optional<AuditLog> findById(UUID id);

    Page<AuditLog> search(AuditLogSearchCriteria criteria, Pageable pageable);
}
