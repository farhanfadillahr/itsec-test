package com.itsectest.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itsectest.auth.domain.LoginOutcome;
import com.itsectest.auth.internal.AuthService;
import com.itsectest.auth.web.dto.ChallengeRequest;
import com.itsectest.auth.web.dto.LoginRequest;
import com.itsectest.auth.web.dto.LoginResponse;
import com.itsectest.auth.web.dto.LogoutRequest;
import com.itsectest.auth.web.dto.MfaChallengeResponse;
import com.itsectest.auth.web.dto.MfaVerifyRequest;
import com.itsectest.auth.web.dto.ProfileResponse;
import com.itsectest.auth.web.dto.RefreshRequest;
import com.itsectest.auth.web.dto.RegisterRequest;
import com.itsectest.auth.web.dto.TokenResponse;
import com.itsectest.shared.error.UnauthorizedException;
import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.web.ApiResponse;
import com.itsectest.user.api.RegisterUserCommand;
import com.itsectest.user.api.UserFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Sign-up, sign-in, MFA and session lifecycle")
public class AuthController {

    private final AuthService auth;
    private final UserFacade users;
    private final CurrentUserProvider currentUser;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register",
            description = "Creates a VIEWER account. Roles are only ever granted by a super admin.")
    public ResponseEntity<ApiResponse<ProfileResponse>> register(@Valid @RequestBody RegisterRequest request) {
        ProfileResponse profile = ProfileResponse.from(auth.register(new RegisterUserCommand(
                request.fullname(), request.username(), request.email(), request.password())));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Registration successful", profile));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with a username or email",
            description = """
                    Returns tokens directly when MFA is off for the account. Otherwise emails a
                    six-digit code and returns a `challengeId` to pass to `/auth/mfa/verify`.

                    Five failures inside ten minutes lock the account for thirty minutes.
                    """)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginOutcome outcome = auth.login(request.identifier(), request.password());

        return switch (outcome) {
            case LoginOutcome.Authenticated authenticated -> ApiResponse.of("Signed in",
                    LoginResponse.authenticated(TokenResponse.from(authenticated.tokens())));
            case LoginOutcome.MfaChallenge challenge -> ApiResponse.of("Verification code sent",
                    LoginResponse.challenge(new MfaChallengeResponse(
                            challenge.challengeId(), challenge.maskedEmail(), challenge.expiresIn())));
        };
    }

    @PostMapping("/mfa/verify")
    @SecurityRequirements
    @Operation(summary = "Complete sign-in with the emailed code")
    public ApiResponse<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ApiResponse.of("Signed in",
                TokenResponse.from(auth.verifyMfa(request.challengeId(), request.code())));
    }

    @PostMapping("/mfa/resend")
    @SecurityRequirements
    @Operation(summary = "Send a fresh code for an existing challenge",
            description = "Subject to a cooldown; the challenge id stays the same.")
    public ApiResponse<MfaChallengeResponse> resendOtp(@Valid @RequestBody ChallengeRequest request) {
        return ApiResponse.of("Verification code sent",
                MfaChallengeResponse.from(auth.resendOtp(request.challengeId())));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new pair",
            description = "The presented refresh token is consumed; keep the new one.")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.of("Token refreshed", TokenResponse.from(auth.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out",
            description = "Revokes the presented access token for its remaining lifetime, "
                    + "and the refresh token if one is supplied.")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) LogoutRequest request) {

        if (jwt == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        auth.logout(jwt.getId(), jwt.getExpiresAt(), request == null ? null : request.refreshToken());
        return ApiResponse.message("Signed out");
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in account")
    public ApiResponse<ProfileResponse> me() {
        return ApiResponse.of("Profile retrieved", users.findById(currentUser.require().userId())
                .map(ProfileResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists")));
    }
}
