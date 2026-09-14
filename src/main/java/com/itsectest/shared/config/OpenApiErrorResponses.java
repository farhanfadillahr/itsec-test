package com.itsectest.shared.config;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itsectest.shared.error.ApiError;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class OpenApiErrorResponses {

    private static final Map<String, String> COMMON = Map.of(
            "400", "Payload or parameters failed validation",
            "401", "Missing, expired or revoked access token",
            "403", "Authenticated, but the role or ownership rule forbids this",
            "404", "No such resource",
            "429", "Rate limit exceeded; see the Retry-After header",
            "500", "Unexpected server error");

    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance(true).readAll(new AnnotatedType(ApiError.class))
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            Content content = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));

            openApi.getPaths().entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith(OpenApiConfig.ACTUATOR_PREFIX))
                    .flatMap(entry -> entry.getValue().readOperations().stream())
                    .forEach(operation -> addMissing(operation, content));
        };
    }

    private void addMissing(Operation operation, Content content) {
        ApiResponses responses = operation.getResponses();
        COMMON.forEach((status, description) -> {
            if (responses.get(status) == null) {
                responses.addApiResponse(status,
                        new ApiResponse().description(description).content(content));
            }
        });
    }
}
