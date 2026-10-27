package com.devMetrics.develop.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPickerResolverTest {

    private OAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "repo")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .build();
        resolver = SecurityConfig.accountPickerResolver(new InMemoryClientRegistrationRepository(github));
    }

    private OAuth2AuthorizationRequest resolve(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return resolver.resolve(request);
    }

    @Test
    void asksGitHubToShowTheAccountPicker() {
        OAuth2AuthorizationRequest request = resolve("/oauth2/authorization/github");

        assertThat(request).isNotNull();
        assertThat(request.getAdditionalParameters()).containsEntry("prompt", "select_account");
        assertThat(request.getAuthorizationRequestUri()).contains("prompt=select_account");
    }

    @Test
    void keepsTheDefaultAuthorizationParameters() {
        OAuth2AuthorizationRequest request = resolve("/oauth2/authorization/github");

        assertThat(request.getClientId()).isEqualTo("test-client");
        assertThat(request.getScopes()).containsExactlyInAnyOrder("read:user", "repo");
        assertThat(request.getState()).isNotBlank();
        assertThat(request.getRedirectUri()).endsWith("/login/oauth2/code/github");
        assertThat(request.getAdditionalParameters())
                .containsEntry("code_challenge_method", "S256")
                .containsKey("code_challenge");
    }

    @Test
    void ignoresRequestsOutsideTheAuthorizationEndpoint() {
        assertThat(resolve("/api/repositories")).isNull();
    }
}
