package com.itsectest.audit.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itsectest.audit.domain.AuditLogSearchCriteria;
import com.itsectest.audit.internal.AuditLogService;
import com.itsectest.audit.web.dto.AuditLogResponse;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.web.ApiResponse;
import com.itsectest.shared.web.PageResponse;
import com.itsectest.shared.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Audit log API, super admin only")
public class AuditLogController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "action", "status", "actorUsername");

    private final AuditLogService auditLogs;

    @GetMapping
    @Operation(summary = "Search the audit trail",
            description = """
                    Filter by actor, action, outcome, resource or time range. `from` and `to` take
                    ISO-8601 instants, for example `2026-09-01T00:00:00Z`.
                    """)
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Pageable pageable = PageableFactory.of(page, size, sortBy, direction, SORTABLE);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                actorId, actorUsername, action, status, resourceType, resourceId, from, to);

        return ApiResponse.of("Audit logs retrieved",
                PageResponse.from(auditLogs.search(criteria, pageable), AuditLogResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one audit entry")
    public ApiResponse<AuditLogResponse> get(@PathVariable UUID id) {
        return ApiResponse.of("Audit log retrieved", AuditLogResponse.from(auditLogs.get(id)));
    }
}
