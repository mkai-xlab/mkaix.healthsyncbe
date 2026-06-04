package com.g93.be.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Thành phần tạo và giải mã Access Token / Refresh Token bằng thư viện JJWT.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.access-secret}") String accessSecret,
            @Value("${app.jwt.refresh-secret}") String refreshSecret,
            @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs,
            @Value("${app.jwt.issuer}") String issuer) {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecret));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecret));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.issuer = issuer;
    }

    /**
     * Tạo Access Token chứa Role và Full Name
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", userDetails.getUser().getRole().name());
        extraClaims.put("fullName", userDetails.getUser().getFullName());
        return buildToken(extraClaims, userDetails.getUsername(), accessExpirationMs, accessKey);
    }

    /**
     * Tạo Refresh Token
     */
    public String generateRefreshToken(CustomUserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), refreshExpirationMs, refreshKey);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs, SecretKey key) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractUsernameFromAccessToken(String token) {
        return extractClaim(token, accessKey, Claims::getSubject);
    }

    public String extractUsernameFromRefreshToken(String token) {
        return extractClaim(token, refreshKey, Claims::getSubject);
    }

    public <T> T extractClaim(String token, SecretKey key, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, key);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsernameFromAccessToken(token);
            return (username.equals(userDetails.getUsername())) && !isTokenExpired(token, accessKey);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsernameFromRefreshToken(token);
            return (username.equals(userDetails.getUsername())) && !isTokenExpired(token, refreshKey);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token, SecretKey key) {
        return extractExpiration(token, key).before(new Date());
    }

    private Date extractExpiration(String token, SecretKey key) {
        return extractClaim(token, key, Claims::getExpiration);
    }
}
