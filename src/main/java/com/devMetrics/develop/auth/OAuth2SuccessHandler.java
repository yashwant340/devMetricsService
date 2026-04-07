package com.devMetrics.develop.auth;

import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        // 1. Extract the OAuth2 user info from the authentication object
        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;

        OAuth2User oauthUser = oauthToken.getPrincipal();

        // 2. Get the GitHub access token via the authorized client service
        OAuth2AuthorizedClient authorizedClient = authorizedClientService
                .loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            log.error("No authorized client found for user: {}", oauthToken.getName());
            response.sendRedirect(frontendUrl + "/login?error=token_missing");
            return;
        }

        String githubAccessToken = authorizedClient
                .getAccessToken()
                .getTokenValue();

        // 3. Extract user attributes from GitHub's /user response
        Map<String, Object> attributes = oauthUser.getAttributes();

        String githubId   = String.valueOf(attributes.get("id"));
        String login      = (String) attributes.get("login");
        String avatarUrl  = (String) attributes.get("avatar_url");
        // Email may be null if user has no public email — handled in upsert
        String email      = (String) attributes.get("email");

        // 4. Upsert the user in PostgreSQL
        User user = upsertUser(githubId, login, avatarUrl, email, githubAccessToken);

        // 5. Issue our own JWT
        String jwt = jwtService.issueToken(user);

        log.info("OAuth2 login successful for GitHub user: {}", login);

        // 6. Redirect to React with the token as a query param.
        //    React immediately reads it, stores it, and replaces the URL.
        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/auth/callback")
                .queryParam("token", jwt)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private User upsertUser(
            String githubId,
            String login,
            String avatarUrl,
            String email,
            String accessToken) {

        return userRepository.findByGithubId(githubId)
                .map(existing -> {
                    // Update mutable fields on every login
                    existing.setLogin(login);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setAccessToken(accessToken);
                    if (email != null) existing.setEmail(email);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .githubId(githubId)
                            .login(login)
                            .avatarUrl(avatarUrl)
                            .email(email)
                            .accessToken(accessToken)
                            .build();
                    return userRepository.save(newUser);
                });
    }
}
