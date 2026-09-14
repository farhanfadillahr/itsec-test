package com.itsectest.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/articles");

    @SuppressWarnings("unused")
    private void sampleHandlerMethod(String payload) {
    }

    @Test
    void mapsADomainExceptionToItsOwnStatusAndCode() {
        ResponseEntity<ApiError> response = handler.handleApi(new NotFoundException("Article not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Article not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/articles");
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void carriesEveryStatusItsCodeDeclares() {
        assertThat(handler.handleApi(new ConflictException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleApi(new ForbiddenException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleApi(new UnauthorizedException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleApi(new InvalidCredentialsException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleApi(new OtpException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleApi(new RateLimitExceededException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handler.handleApi(new AccountLockedException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void listsEveryRejectedFieldForABodyValidationFailure() throws Exception {
        Method method = getClass().getDeclaredMethod("sampleHandlerMethod", String.class);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "must be a well-formed email address"));
        binding.addError(new FieldError("request", "password", "must be at least 8 characters"));

        ResponseEntity<ApiError> response = handler.handleBodyValidation(
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).hasSize(2)
                .extracting(ApiError.FieldViolation::field)
                .containsExactlyInAnyOrder("email", "password");
    }

    @Test
    void reportsQueryParameterViolationsByTheirLeafName() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("list.arg0.size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        ResponseEntity<ApiError> response = handler.handleParamValidation(
                new ConstraintViolationException(Set.of(violation)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).singleElement()
                .extracting(ApiError.FieldViolation::field).isEqualTo("size");
    }

    @Test
    void explainsAnUnparseableBodyWithoutEchoingIt() {
        ResponseEntity<ApiError> response = handler.handleUnreadable(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(new HttpMessageNotReadableException("x", null)).isNotNull();
    }

    @Test
    void namesTheParameterWhoseTypeIsWrong() throws Exception {
        Method method = getClass().getDeclaredMethod("sampleHandlerMethod", String.class);
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                "not-a-uuid", java.util.UUID.class, "id", new MethodParameter(method, 0), null);

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(mismatch, request);

        assertThat(response.getBody().errors()).singleElement()
                .extracting(ApiError.FieldViolation::field).isEqualTo("id");
    }

    @Test
    void turnsAnAuthorisationFailureIntoAPlainForbidden() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to perform this action");
        assertThat(new AccessDeniedException("denied")).isNotNull();
    }

    @Test
    void hidesDatabaseDetailBehindAConflict() {
        ResponseEntity<ApiError> response = handler.handleIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint "
                        + "\"uq_users_email\" Detail: Key (lower(email))=(a@b.c) already exists."),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("The request conflicts with existing data");
        assertThat(response.getBody().message()).doesNotContain("uq_users_email");
    }

    @Test
    void answersAnUnknownPathWithNotFound() {
        assertThat(handler.handleNoResource(request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(NoResourceFoundException.class).isNotNull();
    }

    @Test
    void neverLeaksAnUnexpectedExceptionMessage() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("connection string user=admin password=hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Something went wrong. Please try again later.");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }

    @Test
    void exposesTheCauseOnAnApiExceptionForLogging() {
        Throwable cause = new IllegalStateException("root");
        ApiException exception = new ApiException(ErrorCode.INTERNAL_ERROR, "wrapped", cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCode().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
