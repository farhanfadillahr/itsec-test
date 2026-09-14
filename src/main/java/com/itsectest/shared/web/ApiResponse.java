package com.itsectest.shared.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Envelope wrapping every successful response")
public record ApiResponse<T>(
        @Schema(example = "true") boolean success,
        @Schema(example = "Article created") String message,
        T data,
        Instant timestamp) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, Instant.now());
    }
}
