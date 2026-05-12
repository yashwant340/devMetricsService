package com.devMetrics.develop.controller;

import com.devMetrics.develop.auth.JwtService;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    // Called by React on every app load to get the logged-in user's info.
    // If the access token cookie is valid, Spring's filter has already
    // populated the SecurityContext — we just read from it.
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "id",       Objects.requireNonNull(user).getId(),
                "login",    user.getLogin(),
                "avatar",   user.getAvatarUrl(),
                "email",    user.getEmail() != null ? user.getEmail() : ""
        ));
    }

    // Called by React when the access token has expired (401 response).
    // Validates the refresh token cookie and issues a new access token.
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request,
                                     HttpServletResponse response) {

        String refreshToken = extractCookie(request, "refresh_token");

        if (refreshToken == null) {
            return ResponseEntity.status(401)
                    .body("No refresh token present");
        }

        if (!jwtService.isValid(refreshToken)
                || !jwtService.isRefreshToken(refreshToken)) {
            return ResponseEntity.status(401)
                    .body("Invalid or expired refresh token");
        }

        Optional<UUID> userIdOpt = jwtService.extractUserId(refreshToken);
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Cannot identify user");
        }

        return userRepository.findById(userIdOpt.get())
                .map(user -> {
                    String newAccessToken = jwtService.issueAccessToken(user);
                    addCookie(response, "access_token", newAccessToken,
                            (int) Duration.ofMinutes(15).getSeconds());
                    log.info("Access token refreshed for: {}", user.getLogin());
                    return ResponseEntity.ok(Map.of("status", "refreshed"));
                })
                .orElse(ResponseEntity.status(401)
                        .body(Map.of("error", "User no longer exists")));
    }

    // Clears both cookies — effectively logs the user out
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        clearCookie(response, "access_token");
        clearCookie(response, "refresh_token");
        return ResponseEntity.ok(Map.of("status", "logged out"));
    }

    private void addCookie(HttpServletResponse response,
                           String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)           // maxAge 0 = delete immediately
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
