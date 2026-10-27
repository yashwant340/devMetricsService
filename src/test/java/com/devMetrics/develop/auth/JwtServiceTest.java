package com.devMetrics.develop.auth;

import com.devMetrics.develop.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret",
                Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 60_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiryMs", 120_000L);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setLogin("octocat");
    }

    @Test
    void acceptsATokenItIssued() {
        assertThat(jwtService.isValid(jwtService.issueAccessToken(user))).isTrue();
    }

    @Test
    void rejectsMalformedTokensWithoutThrowing() {
        assertThat(jwtService.isValid("garbage")).isFalse();
        assertThat(jwtService.isValid("a.b.c")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void rejectsATokenWithATamperedSignature() {
        String token = jwtService.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("AA") ? "BB" : "AA");
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsAnExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", -1_000L);
        assertThat(jwtService.isValid(jwtService.issueAccessToken(user))).isFalse();
    }
}
