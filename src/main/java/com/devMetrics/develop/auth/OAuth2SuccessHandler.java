package com.devMetrics.develop.auth;

import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        OAuth2AuthorizedClient authorizedClient = authorizedClientService
                .loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );

        if (authorizedClient == null
                || authorizedClient.getAccessToken() == null) {
            log.error("No authorized client found: {}", oauthToken.getName());
            response.sendRedirect(frontendUrl + "/login?error=token_missing");
            return;
        }

        String githubAccessToken = authorizedClient
                .getAccessToken().getTokenValue();

        Map<String, Object> attributes = Objects.requireNonNull(oauthUser).getAttributes();
        String githubId  = String.valueOf(attributes.get("id"));
        String login     = (String) attributes.get("login");
        String avatarUrl = (String) attributes.get("avatar_url");
        String email     = (String) attributes.get("email");

        User user = upsertUser(
                githubId, login, avatarUrl, email, githubAccessToken);

        // Issue both tokens
        String accessToken  = jwtService.issueAccessToken(user);
        String refreshToken = jwtService.issueRefreshToken(user);

        // Write access token cookie — short lived (15 min)
        addCookie(response, "access_token", accessToken,
                (int) Duration.ofMinutes(15).getSeconds());

        // Write refresh token cookie — long lived (7 days)
        addCookie(response, "refresh_token", refreshToken,
                (int) Duration.ofDays(7).getSeconds());

        log.info("OAuth2 login successful for: {}", login);

        // Redirect to React — no token in URL
        response.sendRedirect(frontendUrl + "/auth/callback");
    }

    private void addCookie(HttpServletResponse response,
                           String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)               // JS cannot read this
                .secure(cookieSecure)         // HTTPS only in prod
                .path("/")                    // sent on all requests
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")              // CSRF protection
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private User upsertUser(String githubId, String login,
                            String avatarUrl, String email,
                            String accessToken) {
        return userRepository.findByGithubId(githubId)
                .map(existing -> {
                    existing.setLogin(login);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setAccessToken(accessToken);
                    if (email != null) existing.setEmail(email);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .githubId(githubId)
                                .login(login)
                                .avatarUrl(avatarUrl)
                                .email(email)
                                .accessToken(accessToken)
                                .build()
                ));
    }
}