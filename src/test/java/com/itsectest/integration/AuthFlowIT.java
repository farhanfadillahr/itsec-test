package com.itsectest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.itsectest.support.IntegrationTest;
import com.itsectest.support.RecordingOtpChannel;

@IntegrationTest
@DisplayName("authentication, end to end")
class AuthFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecordingOtpChannel.CapturedCodes codes;

    private String uniqueName() {
        return "user" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullname":"Test User","username":"%s","email":"%s","password":"Str0ng#Pass"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("VIEWER"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aNewRegistrationIsAlwaysAViewer() throws Exception {
        String username = uniqueName();

        String body = register(username, username + "@example.com");

        assertThat(JsonPath.<String>read(body, "$.data.username")).isEqualTo(username);
        assertThat(JsonPath.<Boolean>read(body, "$.data.mfaEnabled")).isTrue();
    }

    @Test
    void theSameUsernameCannotBeRegisteredTwice() throws Exception {
        String username = uniqueName();
        register(username, username + "@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullname":"Other","username":"%s","email":"other-%s@example.com",
                                 "password":"Str0ng#Pass"}
                                """.formatted(username, username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void aWeakPasswordIsRejectedFieldByField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullname":"Test","username":"weakling","email":"weak@example.com",
                                 "password":"password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void aCorrectPasswordAloneDoesNotGrantAToken() throws Exception {
        String username = uniqueName();
        register(username, username + "@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Str0ng#Pass"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.mfa.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.tokens").doesNotExist());
    }

    @Test
    void loggingInWorksWithTheEmailJustAsWellAsTheUsername() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        register(username, email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Str0ng#Pass"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true));
    }

    @Test
    void theEmailedCodeCompletesTheSignIn() throws Exception {
        String username = uniqueName();
        String email = username + "@example.com";
        register(username, email);

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Str0ng#Pass"}
                                """.formatted(username)))
                .andReturn().getResponse().getContentAsString();

        String challengeId = JsonPath.read(login, "$.data.mfa.challengeId");
        String accessToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"%s"}
                                """.formatted(challengeId, codes.forEmail(email))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void awrongCodeIsRefusedAndCounted() throws Exception {
        String username = uniqueName();
        register(username, username + "@example.com");

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Str0ng#Pass"}
                                """.formatted(username)))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"000000"}
                                """.formatted(JsonPath.<String>read(login, "$.data.mfa.challengeId"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("remaining")));
    }

    @Test
    void anUnauthenticatedRequestIsRefusedWithTheStandardErrorShape() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aForgedTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsReachableWithoutATokenSoTheApiCanBeExplored() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ITSEC Test API"));
    }

    @Test
    void errorResponsesReferenceASchemaThatExists() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/articles'].get.responses['401'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.FieldViolation.properties.field").exists());
    }

    @Test
    void healthEndpointIsDocumentedAsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/actuator/health'].get").exists())
                .andExpect(jsonPath("$.paths['/actuator/health'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/actuator/health'].get.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator']").doesNotExist());
    }
}
