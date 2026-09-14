package com.itsectest.shared.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String ACTUATOR_PREFIX = "/actuator";

    @Bean
    public OpenAPI itsecTestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ITSEC Test API")
                        .version("1.0.0")
                        .description("""
                                Article API with JWT authentication, email OTP, role based access control,
                                account lockout and audit logging.

                                To get a token:
                                1. Call `POST /api/v1/auth/login` with a username or email and the password.
                                2. If MFA is enabled for the account, the response contains a `challengeId`.
                                   Call `POST /api/v1/auth/mfa/verify` with it and the code from the email.
                                3. Click Authorize and paste the `accessToken`.

                                MFA is disabled for the `superadmin` account, so step 2 is not needed there.
                                """)
                        .contact(new Contact().name("ITSEC Test"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token returned by the login or MFA-verify endpoint")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer publicActuatorEndpoints() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith(ACTUATOR_PREFIX)) {
                    item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
                }
            });
        };
    }
}
