package com.itsectest.shared.audit.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private final ApplicationEventPublisher events;
    private final CurrentUserProvider currentUser;

    public void record(AuditAction action, AuditStatus status, String resourceType,
            String resourceId, Map<String, Object> detail) {

        AuthPrincipal principal = currentUser.find().orElse(null);
        publish(AuditEvent.builder()
                .actorId(principal == null ? null : principal.userId())
                .actorUsername(principal == null ? null : principal.username())
                .action(action)
                .status(status)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .build());
    }

    public void recordForSubject(UUID actorId, String actorUsername, AuditAction action,
            AuditStatus status, Map<String, Object> detail) {

        publish(AuditEvent.builder()
                .actorId(actorId)
                .actorUsername(actorUsername)
                .action(action)
                .status(status)
                .detail(detail)
                .build());
    }

    public void publish(AuditEvent event) {
        AuditEvent enriched = event.request() == null || event.request().endpoint() == null
                ? withCurrentRequest(event)
                : event;
        events.publishEvent(enriched);
    }

    private AuditEvent withCurrentRequest(AuditEvent event) {
        return AuditEvent.builder()
                .actorId(event.actorId())
                .actorUsername(event.actorUsername())
                .action(event.action())
                .status(event.status())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .detail(event.detail())
                .request(currentRequestMetadata())
                .occurredAt(event.occurredAt())
                .build();
    }

    private RequestMetadata currentRequestMetadata() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return RequestMetadata.from(attributes.getRequest());
        }
        return RequestMetadata.empty();
    }
}
