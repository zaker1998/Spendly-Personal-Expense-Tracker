package com.spendly.web;

import com.spendly.dto.AuthDtos.AuthResponse;
import com.spendly.dto.AuthDtos.ChangePasswordRequest;
import com.spendly.dto.AuthDtos.LoginRequest;
import com.spendly.dto.AuthDtos.RegisterRequest;
import com.spendly.security.SecurityUtils;
import com.spendly.service.AuthService;
import com.spendly.service.AuthService.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    /**
     * The cookie is scoped to the auth endpoints, so it is not attached to every
     * API call the SPA makes — only to the two that actually need it.
     */
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final Duration cookieMaxAge;

    public AuthController(
            AuthService authService,
            @Value("${spendly.refresh.cookie-name:spendly_refresh}") String cookieName,
            @Value("${spendly.refresh.cookie-secure:true}") boolean cookieSecure,
            @Value("${spendly.refresh.cookie-same-site:Lax}") String cookieSameSite,
            @Value("${spendly.refresh.expiration-ms:2592000000}") long refreshExpirationMs
    ) {
        this.authService = authService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieMaxAge = Duration.ofMillis(refreshExpirationMs);
    }

    @PostMapping("/register")
    @Operation(summary = "Register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return respond(HttpStatus.OK, authService.login(request, httpRequest.getRemoteAddr()));
    }

    /**
     * Swaps the refresh cookie for a new access token, rotating the cookie.
     *
     * <p>No CSRF token guards this, and it does not need one: a cross-site page
     * can cause the call, but the browser will not let it read the response, so
     * the only effect is that the victim's refresh token rotates. Nothing is
     * disclosed and nothing is changed on the account.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${spendly.refresh.cookie-name:spendly_refresh}", required = false)
            String refreshToken
    ) {
        return respond(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "End this session")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${spendly.refresh.cookie-name:spendly_refresh}", required = false)
            String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the password and end every other session")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return respond(HttpStatus.OK, authService.changePassword(SecurityUtils.currentUserId(), request));
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .body(session.body());
    }

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(cookieMaxAge).build();
    }

    private ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                // httpOnly is the point of the exercise: script in the page
                // cannot read this, so an XSS that drains localStorage gets an
                // access token with minutes left on it and no way to renew it.
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(COOKIE_PATH);
    }
}
