package com.itsectest.shared.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.AuditPublisher;
import com.itsectest.shared.audit.api.AuditStatus;
import com.itsectest.shared.error.ApiError;
import com.itsectest.shared.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditPublisher auditPublisher;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        auditPublisher.record(AuditAction.ACCESS_DENIED, AuditStatus.FAILURE, "ENDPOINT",
                request.getRequestURI(), Map.of("method", request.getMethod()));

        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(
                ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action",
                request.getRequestURI())));
    }
}
