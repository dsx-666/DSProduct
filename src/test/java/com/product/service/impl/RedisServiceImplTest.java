package com.product.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceImplTest {
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String,String> valueOperations;
    private RedisServiceImpl redisService;

    @BeforeEach
    void setUp() {
        redisService = new RedisServiceImpl(redisTemplate, new ObjectMapper());
    }

    @Test
    void bloomFilterRejectsCertainMissingIdsButKeepsExistingIds() {
        redisService.initializeBloomFilter("product", () -> List.of(1L, 2L, 100L));

        assertTrue(redisService.mightContain("product", 1L));
        assertTrue(redisService.mightContain("product", 100L));
        assertFalse(redisService.mightContain("product", 999999L));
    }

    @Test
    void nullCacheUsesRandomTtlInsideConfiguredRange() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        redisService.cacheNull("empty:1", Duration.ofSeconds(30), Duration.ofSeconds(60));

        verify(valueOperations).set(eq("empty:1"), eq("__NULL__"), ttlCaptor.capture());
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ofSeconds(30)) >= 0);
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ofSeconds(60)) <= 0);
    }

    @Test
    void mutexCacheLoadsOnceWritesResultAndReleasesOwnedLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache:key")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("cache:key:lock"), anyString(),
                eq(Duration.ofSeconds(10)))).thenReturn(true);
        AtomicInteger loadCount = new AtomicInteger();

        Map<String,Object> result = redisService.getOrLoadWithMutex(
                "cache:key", Duration.ofSeconds(20), Duration.ofSeconds(40),
                new TypeReference<Map<String,Object>>() {},
                () -> { loadCount.incrementAndGet(); return Map.of("value", 1); });

        assertEquals(1, loadCount.get());
        assertEquals(1, result.get("value"));
        verify(valueOperations).set(eq("cache:key"), contains("\"value\":1"), any(Duration.class));
        verify(redisTemplate).execute(any(), eq(List.of("cache:key:lock")), anyString());
    }
}
