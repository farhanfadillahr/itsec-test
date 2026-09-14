package com.itsectest.audit.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.itsectest.audit.domain.AuditLog;

interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
