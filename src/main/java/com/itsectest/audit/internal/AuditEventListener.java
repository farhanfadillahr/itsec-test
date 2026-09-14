package com.itsectest.audit.internal;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogRepository;
import com.itsectest.shared.audit.api.AuditEvent;
import com.itsectest.shared.audit.api.RequestMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogs;
    private final DeviceParser deviceParser;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AuditEvent event) {
        try {
            RequestMetadata request = event.request();
            DeviceInfo device = deviceParser.parse(request.userAgent());

            auditLogs.append(AuditLog.builder()
                    .actorId(event.actorId())
                    .actorUsername(event.actorUsername())
                    .action(event.action())
                    .status(event.status())
                    .resourceType(event.resourceType())
                    .resourceId(event.resourceId())
                    .httpMethod(request.httpMethod())
                    .endpoint(request.endpoint())
                    .ipAddress(request.ipAddress())
                    .userAgent(request.userAgent())
                    .browser(device.browser())
                    .browserVersion(device.browserVersion())
                    .os(device.os())
                    .deviceType(device.deviceType())
                    .detail(event.detail())
                    .createdAt(event.occurredAt())
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to persist audit event {} for actor {}", event.action(), event.actorId(), ex);
        }
    }
}
