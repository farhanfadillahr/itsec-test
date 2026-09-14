package com.itsectest.shared.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.audit.api.Auditable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditableAspectTest {

    static class SampleService {

        @Auditable(action = AuditAction.ARTICLE_UPDATED, resourceType = "ARTICLE", resourceId = "#id")
        String update(UUID id, String title) {
            return title;
        }

        @Auditable(action = AuditAction.ARTICLE_CREATED, resourceType = "ARTICLE", resourceId = "#result.id()")
        Created create() {
            return new Created(UUID.randomUUID());
        }

        @Auditable(action = AuditAction.USER_LIST_VIEWED)
        void list() {
        }

        @Auditable(action = AuditAction.ARTICLE_VIEWED, resourceType = "ARTICLE", resourceId = "#nonexistent.")
        void broken() {
        }
    }

    record Created(UUID id) {
    }

    @Mock private AuditPublisher publisher;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private AuditableAspect aspect;

    private Auditable annotationOn(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = SampleService.class.getDeclaredMethod(methodName, parameterTypes);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new SampleService());
        return method.getAnnotation(Auditable.class);
    }

    @BeforeEach
    void setUp() {
        aspect = new AuditableAspect(publisher);
    }

    @Test
    void recordsASuccessAndReturnsTheOriginalResult() throws Throwable {
        Auditable auditable = annotationOn("update", UUID.class, String.class);
        UUID id = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { id, "New title" });
        when(joinPoint.proceed()).thenReturn("New title");

        assertThat(aspect.audit(joinPoint, auditable)).isEqualTo("New title");

        verify(publisher).record(eq(AuditAction.ARTICLE_UPDATED), eq(AuditStatus.SUCCESS),
                eq("ARTICLE"), eq(id.toString()), eq(null));
    }

    @Test
    void resolvesTheResourceIdFromTheReturnValue() throws Throwable {
        Auditable auditable = annotationOn("create");
        Created created = new Created(UUID.randomUUID());
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(created);

        aspect.audit(joinPoint, auditable);

        verify(publisher).record(any(), eq(AuditStatus.SUCCESS), eq("ARTICLE"),
                eq(created.id().toString()), eq(null));
    }

    @Test
    void recordsAFailureAndRethrows() throws Throwable {
        Auditable auditable = annotationOn("update", UUID.class, String.class);
        UUID id = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { id, "x" });
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("nope"));

        assertThatThrownBy(() -> aspect.audit(joinPoint, auditable)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.captor();
        verify(publisher).record(eq(AuditAction.ARTICLE_UPDATED), eq(AuditStatus.FAILURE),
                eq("ARTICLE"), eq(id.toString()), detail.capture());
        assertThat(detail.getValue())
                .containsEntry("error", "IllegalStateException")
                .containsEntry("reason", "nope");
    }

    @Test
    void copesWithAnActionThatHasNoResource() throws Throwable {
        Auditable auditable = annotationOn("list");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.audit(joinPoint, auditable);

        verify(publisher).record(eq(AuditAction.USER_LIST_VIEWED), eq(AuditStatus.SUCCESS),
                eq(null), eq(null), eq(null));
    }

    @Test
    void aBrokenExpressionDegradesToAMissingIdRatherThanBreakingTheCall() throws Throwable {
        Auditable auditable = annotationOn("broken");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.audit(joinPoint, auditable)).isNull();
        verify(publisher).record(any(), eq(AuditStatus.SUCCESS), eq("ARTICLE"), eq(null), eq(null));
    }
}
