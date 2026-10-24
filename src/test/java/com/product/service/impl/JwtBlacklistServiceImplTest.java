package com.product.service.impl;

import com.product.pojo.other.JwtUtil;
import com.product.service.RedisService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceImplTest {
    @Mock
    private RedisService redisService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private Claims claims;

    private JwtBlacklistServiceImpl jwtBlacklistService;

    @BeforeEach
    void setUp() {
        jwtBlacklistService = new JwtBlacklistServiceImpl(
                redisService,jwtUtil
        );
    }

    @Test
    void shouldAddTokenHashToRedisUntilJwtExpires() {
        when(jwtUtil.parseToken("jwt-token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(
                new Date(System.currentTimeMillis() + 60000)
        );
        jwtBlacklistService.addBlacklist("jwt-token");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor =
                ArgumentCaptor.forClass(Duration.class);
        verify(redisService).setString(
                keyCaptor.capture(),anyString(),durationCaptor.capture()
        );
        assertTrue(keyCaptor.getValue().startsWith("jwt:blacklist:"));
        assertFalse(keyCaptor.getValue().contains("jwt-token"));
        assertTrue(durationCaptor.getValue().toMillis() > 0);
        assertTrue(durationCaptor.getValue().toMillis() <= 60000);
    }

    @Test
    void shouldNotStoreExpiredToken() {
        when(jwtUtil.parseToken("jwt-token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(
                new Date(System.currentTimeMillis() - 1000)
        );

        jwtBlacklistService.addBlacklist("jwt-token");

        verify(redisService,never()).setString(anyString(),anyString(),any());
    }

    @Test
    void shouldCheckTokenHashInRedis() {
        when(redisService.hasKey(anyString())).thenReturn(true);

        boolean blacklisted = jwtBlacklistService.isBlacklisted("jwt-token");

        assertTrue(blacklisted);
        verify(redisService).hasKey(anyString());
    }

    @Test
    void shouldKeepRepeatedLogoutIdempotent() {
        when(redisService.hasKey(anyString())).thenReturn(true);

        jwtBlacklistService.addBlacklist("jwt-token");

        verify(jwtUtil,never()).parseToken("jwt-token");
        verify(redisService,never()).setString(anyString(),anyString(),any());
    }
}
