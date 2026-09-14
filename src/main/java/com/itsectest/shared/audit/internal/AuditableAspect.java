package com.itsectest.shared.audit.internal;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.audit.api.Auditable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class AuditableAspect {

    private final AuditPublisher publisher;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            publisher.record(auditable.action(), AuditStatus.SUCCESS, blankToNull(auditable.resourceType()),
                    resolveResourceId(auditable, joinPoint, result), null);
            return result;
        } catch (Throwable failure) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("error", failure.getClass().getSimpleName());
            detail.put("reason", failure.getMessage());
            publisher.record(auditable.action(), AuditStatus.FAILURE, blankToNull(auditable.resourceType()),
                    resolveResourceId(auditable, joinPoint, null), detail);
            throw failure;
        }
    }

    private String resolveResourceId(Auditable auditable, ProceedingJoinPoint joinPoint, Object result) {
        if (auditable.resourceId().isBlank()) {
            return null;
        }
        if (result == null && auditable.resourceId().contains("#result")) {
            return null;
        }
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNames);
            context.setVariable("result", result);
            Expression expression = parser.parseExpression(auditable.resourceId());
            Object value = expression.getValue(context);
            return value == null ? null : value.toString();
        } catch (RuntimeException ex) {
            log.warn("Could not evaluate audit resourceId expression '{}'", auditable.resourceId(), ex);
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
