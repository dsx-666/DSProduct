package com.product.service.impl;

import com.product.pojo.other.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String,String> valueOperations;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private Claims claims;

    private JwtBlacklistServiceImpl jwtBlacklistService;

    @BeforeEach
    void setUp() {
        jwtBlacklistService = new JwtBlacklistServiceImpl(
                stringRedisTemplate,jwtUtil
        );
    }

    @Test
    void shouldAddTokenHashToRedisUntilJwtExpires() {
        when(jwtUtil.parseToken("jwt-token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(
                new Date(System.currentTimeMillis() + 60000)
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtBlacklistService.addBlacklist("jwt-token");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor =
                ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(
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

        verify(stringRedisTemplate,never()).opsForValue();
    }

    @Test
    void shouldCheckTokenHashInRedis() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);

        boolean blacklisted = jwtBlacklistService.isBlacklisted("jwt-token");

        assertTrue(blacklisted);
        verify(stringRedisTemplate).hasKey(anyString());
    }

    @Test
    void shouldKeepRepeatedLogoutIdempotent() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);

        jwtBlacklistService.addBlacklist("jwt-token");

        verify(jwtUtil,never()).parseToken("jwt-token");
        verify(stringRedisTemplate,never()).opsForValue();
    }
}
