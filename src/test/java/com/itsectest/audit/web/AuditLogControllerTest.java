package com.itsectest.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogSearchCriteria;
import com.itsectest.audit.internal.AuditLogService;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogControllerTest {

    private static final UUID LOG_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock private AuditLogService auditLogs;

    @InjectMocks private AuditLogController controller;

    private static AuditLog entry() {
        return AuditLog.builder()
                .id(LOG_ID).actorId(ACTOR_ID).actorUsername("farhan")
                .action(AuditAction.LOGIN_SUCCESS).status(AuditStatus.SUCCESS)
                .resourceType("SESSION").resourceId("jti-1")
                .httpMethod("POST").endpoint("/api/v1/auth/login")
                .ipAddress("203.0.113.42").userAgent("curl/8.7.1")
                .browser("curl").browserVersion("8.7.1").os("macOS 15.3").deviceType("API_CLIENT")
                .detail(Map.of("role", "EDITOR")).createdAt(Instant.now())
                .build();
    }

    @Test
    void searchingReturnsEntriesWithTheDeviceBrokenOut() {
        when(auditLogs.search(any(), any())).thenReturn(new PageImpl<>(List.of(entry())));

        var page = controller.search(null, null, null, null, null, null, null, null,
                0, 20, "createdAt", "desc").data();

        assertThat(page.items()).hasSize(1);
        var item = page.items().getFirst();
        assertThat(item.actorUsername()).isEqualTo("farhan");
        assertThat(item.device().browser()).isEqualTo("curl");
        assertThat(item.device().type()).isEqualTo("API_CLIENT");
        assertThat(item.device().userAgent()).isEqualTo("curl/8.7.1");
        assertThat(item.detail()).containsEntry("role", "EDITOR");
    }

    @Test
    void everyFilterReachesTheQuery() {
        when(auditLogs.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-30T23:59:59Z");

        controller.search(ACTOR_ID, "farhan", AuditAction.LOGIN_FAILED, AuditStatus.FAILURE,
                "ARTICLE", "abc", from, to, 0, 20, "createdAt", "desc");

        ArgumentCaptor<AuditLogSearchCriteria> criteria = ArgumentCaptor.captor();
        verify(auditLogs).search(criteria.capture(), any(Pageable.class));
        assertThat(criteria.getValue().actorId()).isEqualTo(ACTOR_ID);
        assertThat(criteria.getValue().action()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(criteria.getValue().status()).isEqualTo(AuditStatus.FAILURE);
        assertThat(criteria.getValue().resourceType()).isEqualTo("ARTICLE");
        assertThat(criteria.getValue().from()).isEqualTo(from);
        assertThat(criteria.getValue().to()).isEqualTo(to);
    }

    @Test
    void readingOneEntryReturnsIt() {
        when(auditLogs.get(LOG_ID)).thenReturn(entry());

        assertThat(controller.get(LOG_ID).data().id()).isEqualTo(LOG_ID);
    }
}
