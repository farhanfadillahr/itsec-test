package com.itsectest.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditEvent;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.audit.api.RequestMetadata;
import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.security.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditPublisherTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private ApplicationEventPublisher events;
    @Mock private CurrentUserProvider currentUser;

    private AuditPublisher publisher;

    private AuditEvent published() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.captor();
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void insideRequest(String method, String uri, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void attributesAnEventToTheAuthenticatedCaller() {
        publisher = new AuditPublisher(events, currentUser);
        when(currentUser.find()).thenReturn(
                Optional.of(new AuthPrincipal(USER_ID, "farhan", Role.EDITOR)));

        publisher.record(AuditAction.ARTICLE_CREATED, AuditStatus.SUCCESS, "ARTICLE", "abc", Map.of("k", "v"));

        AuditEvent event = published();
        assertThat(event.actorId()).isEqualTo(USER_ID);
        assertThat(event.actorUsername()).isEqualTo("farhan");
        assertThat(event.action()).isEqualTo(AuditAction.ARTICLE_CREATED);
        assertThat(event.resourceId()).isEqualTo("abc");
        assertThat(event.detail()).containsEntry("k", "v");
    }

    @Test
    void publishesAnonymouslyWhenThereIsNoSecurityContext() {
        publisher = new AuditPublisher(events, currentUser);
        when(currentUser.find()).thenReturn(Optional.empty());

        publisher.record(AuditAction.ACCESS_DENIED, AuditStatus.FAILURE, "ENDPOINT", "/x", null);

        assertThat(published().actorId()).isNull();
    }

    @Test
    void recordsAFailedLoginAgainstASubjectWhoIsNotAuthenticated() {
        publisher = new AuditPublisher(events, currentUser);

        publisher.recordForSubject(USER_ID, "farhan", AuditAction.LOGIN_FAILED,
                AuditStatus.FAILURE, Map.of("reason", "WRONG_PASSWORD"));

        AuditEvent event = published();
        assertThat(event.actorUsername()).isEqualTo("farhan");
        assertThat(event.status()).isEqualTo(AuditStatus.FAILURE);
    }

    @Test
    void capturesTheRequestWhileItIsStillInFlight() {
        publisher = new AuditPublisher(events, currentUser);
        when(currentUser.find()).thenReturn(Optional.empty());
        insideRequest("POST", "/api/v1/articles", "curl/8.7.1");

        publisher.record(AuditAction.ARTICLE_CREATED, AuditStatus.SUCCESS, "ARTICLE", "abc", null);

        RequestMetadata metadata = published().request();
        assertThat(metadata.httpMethod()).isEqualTo("POST");
        assertThat(metadata.endpoint()).isEqualTo("/api/v1/articles");
        assertThat(metadata.userAgent()).isEqualTo("curl/8.7.1");
        assertThat(metadata.ipAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void leavesAnAlreadyPopulatedRequestAlone() {
        publisher = new AuditPublisher(events, currentUser);
        RequestMetadata explicit = new RequestMetadata("10.0.0.1", "agent", "PUT", "/explicit");

        publisher.publish(AuditEvent.builder()
                .action(AuditAction.USER_UPDATED).status(AuditStatus.SUCCESS).request(explicit).build());

        assertThat(published().request()).isEqualTo(explicit);
    }

    @Test
    void fillsInDefaultsForAnUnderspecifiedEvent() {
        AuditEvent event = AuditEvent.builder().action(AuditAction.LOGOUT).build();

        assertThat(event.status()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(event.request()).isEqualTo(RequestMetadata.empty());
        assertThat(event.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }
}
