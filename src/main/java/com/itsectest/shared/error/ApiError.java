package com.itsectest.shared.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Error response returned by every endpoint")
public record ApiError(
        @Schema(example = "false") boolean success,
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Request payload is invalid") String message,
        @Schema(description = "Present only for payload validation failures") List<FieldViolation> errors,
        @Schema(example = "/api/v1/articles") String path,
        Instant timestamp) {

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(false, code.name(), message, null, path, Instant.now());
    }

    public static ApiError validation(String message, List<FieldViolation> violations, String path) {
        return new ApiError(false, ErrorCode.VALIDATION_ERROR.name(), message, violations, path, Instant.now());
    }

    @Schema(name = "FieldViolation")
    public record FieldViolation(
            @Schema(example = "email") String field,
            @Schema(example = "must be a well-formed email address") String message) {
    }
}
