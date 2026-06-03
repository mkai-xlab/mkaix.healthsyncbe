package com.g93.be.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${app.jwt.access-secret}")
    private String accessSecret;

    @Value("${app.jwt.refresh-secret}")
    private String refreshSecret;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey(String secret) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String username, Map<String, Object> claims) {
        return generateToken(username, claims, accessExpirationMs, accessSecret);
    }

    public String generateRefreshToken(String username) {
        return generateToken(username, new HashMap<>(), refreshExpirationMs, refreshSecret);
    }

    private String generateToken(String username, Map<String, Object> claims, long expirationMs, String secret) {
        return Jwts.builder()
                .subject(username)
                .claims(claims)
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignKey(secret))
                .compact();
    }

    private SecretKey getSignKey(String secret) {
        return getSigningKey(secret);
    }

    public Claims extractAllClaims(String token, String secret) {
        return Jwts.parser()
                .verifyWith(getSigningKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, String secret, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, secret);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token, String secret) {
        return extractClaim(token, secret, Claims::getSubject);
    }

    public Date extractExpiration(String token, String secret) {
        return extractClaim(token, secret, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token, String secret) {
        return extractExpiration(token, secret).before(new Date());
    }

    public boolean validateToken(String token, String secret, String expectedUsername) {
        final String username = extractUsername(token, secret);
        return (username.equals(expectedUsername) && !isTokenExpired(token, secret));
    }

    // Helper methods specifically for access token
    public String extractAccessUsername(String token) {
        return extractUsername(token, accessSecret);
    }

    public boolean isAccessTokenExpired(String token) {
        return isTokenExpired(token, accessSecret);
    }

    public boolean validateAccessToken(String token, String expectedUsername) {
        return validateToken(token, accessSecret, expectedUsername);
    }

    // Helper methods specifically for refresh token
    public String extractRefreshUsername(String token) {
        return extractUsername(token, refreshSecret);
    }

    public boolean isRefreshTokenExpired(String token) {
        return isTokenExpired(token, refreshSecret);
    }

    public boolean validateRefreshToken(String token, String expectedUsername) {
        return validateToken(token, refreshSecret, expectedUsername);
    }
}
