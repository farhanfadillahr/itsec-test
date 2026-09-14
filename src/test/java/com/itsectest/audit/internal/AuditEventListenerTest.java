package com.itsectest.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogRepository;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditEvent;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.audit.api.RequestMetadata;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    private static final String CHROME_ON_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    @Mock private AuditLogRepository auditLogs;

    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(auditLogs, new DeviceParser());
    }

    private AuditLog appended() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.captor();
        verify(auditLogs).append(captor.capture());
        return captor.getValue();
    }

    @Test
    void writesTheEventWithTheDeviceBrokenOut() {
        UUID actor = UUID.randomUUID();
        Instant occurred = Instant.now();

        listener.on(AuditEvent.builder()
                .actorId(actor).actorUsername("farhan")
                .action(AuditAction.ARTICLE_CREATED).status(AuditStatus.SUCCESS)
                .resourceType("ARTICLE").resourceId("abc")
                .detail(Map.of("title", "Something"))
                .request(new RequestMetadata("203.0.113.42", CHROME_ON_MAC, "POST", "/api/v1/articles"))
                .occurredAt(occurred)
                .build());

        AuditLog stored = appended();
        assertThat(stored.getActorId()).isEqualTo(actor);
        assertThat(stored.getActorUsername()).isEqualTo("farhan");
        assertThat(stored.getAction()).isEqualTo(AuditAction.ARTICLE_CREATED);
        assertThat(stored.getHttpMethod()).isEqualTo("POST");
        assertThat(stored.getEndpoint()).isEqualTo("/api/v1/articles");
        assertThat(stored.getIpAddress()).isEqualTo("203.0.113.42");
        assertThat(stored.getBrowser()).isEqualTo("Chrome");
        assertThat(stored.getBrowserVersion()).isEqualTo("141.0.0.0");
        assertThat(stored.getOs()).isEqualTo("macOS 10.15.7");
        assertThat(stored.getDeviceType()).isEqualTo("DESKTOP");
        assertThat(stored.getUserAgent()).isEqualTo(CHROME_ON_MAC);
        assertThat(stored.getCreatedAt()).isEqualTo(occurred);
        assertThat(stored.getDetail()).containsEntry("title", "Something");
    }

    @Test
    void recordsEventsRaisedOutsideAnyRequest() {
        listener.on(AuditEvent.builder().action(AuditAction.LOGIN_FAILED)
                .status(AuditStatus.FAILURE).actorUsername("ghost").build());

        AuditLog stored = appended();
        assertThat(stored.getEndpoint()).isNull();
        assertThat(stored.getBrowser()).isEqualTo("Unknown");
        assertThat(stored.getStatus()).isEqualTo(AuditStatus.FAILURE);
    }

    @Test
    void aFailedAuditWriteNeverEscapes() {
        when(auditLogs.append(any())).thenThrow(new RuntimeException("database is down"));

        assertThatCode(() -> listener.on(AuditEvent.builder()
                .action(AuditAction.LOGOUT).status(AuditStatus.SUCCESS).build()))
                .doesNotThrowAnyException();
    }
}
