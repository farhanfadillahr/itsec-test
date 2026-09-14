package com.itsectest.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(length = 255)
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditStatus status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(length = 60)
    private String browser;

    @Column(name = "browser_version", length = 40)
    private String browserVersion;

    @Column(length = 60)
    private String os;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
