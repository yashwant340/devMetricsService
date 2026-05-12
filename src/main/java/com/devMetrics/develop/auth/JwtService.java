package com.devMetrics.develop.auth;

import com.devMetrics.develop.entity.User;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;       // 15 minutes

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;      // 7 days

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(User user) {
        return buildToken(user, accessTokenExpiryMs, "access");
    }

    public String issueRefreshToken(User user) {
        // Refresh token carries minimal claims — just enough to re-identify
        return buildToken(user, refreshTokenExpiryMs, "refresh");
    }

    private String buildToken(User user, long expiryMs, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("login", user.getLogin())
                .claim("avatar", user.getAvatarUrl())
                .claim("type", type)          // distinguish access vs refresh
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiryMs)))
                .signWith(signingKey())
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    // Extract user ID without throwing — used in refresh flow
    public Optional<UUID> extractUserId(String token) {
        try {
            String subject = validateAndParse(token).getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Confirm the token is specifically a refresh token
    public boolean isRefreshToken(String token) {
        try {
            String type = (String) validateAndParse(token).get("type");
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
}