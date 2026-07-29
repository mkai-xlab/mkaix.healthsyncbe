package com.g93.be.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final String ACCESS_TOKEN_PREFIX = "token:blacklist:access:";
    private static final String REFRESH_TOKEN_PREFIX = "token:blacklist:refresh:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklistAccessToken(String token, Duration remainingValidity) {
        blacklist(ACCESS_TOKEN_PREFIX, token, remainingValidity);
    }

    public void blacklistRefreshToken(String token, Duration remainingValidity) {
        blacklist(REFRESH_TOKEN_PREFIX, token, remainingValidity);
    }

    public boolean isAccessTokenBlacklisted(String token) {
        return isBlacklisted(ACCESS_TOKEN_PREFIX, token);
    }

    public boolean isRefreshTokenBlacklisted(String token) {
        return isBlacklisted(REFRESH_TOKEN_PREFIX, token);
    }

    private void blacklist(String prefix, String token, Duration remainingValidity) {
        long ttlMillis = remainingValidity.toMillis();
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(
                prefix + hash(token),
                "revoked",
                ttlMillis,
                TimeUnit.MILLISECONDS);
    }

    private boolean isBlacklisted(String prefix, String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(prefix + hash(token)));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
