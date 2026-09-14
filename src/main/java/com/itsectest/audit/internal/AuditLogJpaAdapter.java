package com.itsectest.audit.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogRepository;
import com.itsectest.audit.domain.AuditLogSearchCriteria;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class AuditLogJpaAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLog append(AuditLog entry) {
        return jpa.save(entry);
    }

    @Override
    public Optional<AuditLog> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Page<AuditLog> search(AuditLogSearchCriteria criteria, Pageable pageable) {
        return jpa.findAll(AuditLogSpecifications.matching(criteria), pageable);
    }
}
