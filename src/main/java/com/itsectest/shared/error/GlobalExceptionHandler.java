package com.itsectest.shared.error;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        log.debug("Handled {}: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getCode().status())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiError.validation("Request payload is invalid", violations, request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParamValidation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        lastNode(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiError.validation("Request parameters are invalid", violations, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status())
                .body(ApiError.of(ErrorCode.MALFORMED_REQUEST,
                        "Request body is missing or not valid JSON", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiError.validation("Request parameters are invalid",
                        List.of(new ApiError.FieldViolation(ex.getName(), "has the wrong type")),
                        request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiError.of(ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Database constraint violated on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiError.of(ErrorCode.CONFLICT,
                        "The request conflicts with existing data", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ApiError.of(ErrorCode.NOT_FOUND, "No endpoint matches this path",
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR,
                        "Something went wrong. Please try again later.", request.getRequestURI()));
    }

    private static String lastNode(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index < 0 ? propertyPath : propertyPath.substring(index + 1);
    }
}
