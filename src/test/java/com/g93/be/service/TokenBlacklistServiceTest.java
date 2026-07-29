package com.g93.be.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService(redisTemplate);
    }

    @Test
    void blacklistAccessTokenStoresOnlyHashWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklistAccessToken("raw.jwt.token", Duration.ofMinutes(5));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                keyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("revoked"),
                org.mockito.ArgumentMatchers.eq(300_000L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));
        assertTrue(keyCaptor.getValue().startsWith("token:blacklist:access:"));
        assertFalse(keyCaptor.getValue().contains("raw.jwt.token"));
    }

    @Test
    void detectsBlacklistedAccessToken() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertTrue(service.isAccessTokenBlacklisted("raw.jwt.token"));
    }
}
