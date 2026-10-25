package com.product.realtime.websocket;

import com.product.realtime.config.RealtimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeTicketServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    private RealtimeTicketService service;

    @BeforeEach
    void setUp() {
        RealtimeProperties properties = new RealtimeProperties();
        properties.setTicketTtl(Duration.ofSeconds(30));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RealtimeTicketService(redisTemplate, properties);
    }

    @Test
    void shouldConsumeTicketOnlyOnce() {
        RealtimeTicketService.IssuedTicket issued = service.issue(42L);
        when(valueOperations.getAndDelete(anyString()))
                .thenReturn("42")
                .thenReturn(null);

        assertNotNull(issued.ticket());
        assertEquals(42L, service.consume(issued.ticket()).userId());
        assertNull(service.consume(issued.ticket()));
        verify(valueOperations).set(
                anyString(), eq("42"), eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void shouldRejectMissingOrExpiredTicket() {
        RealtimeTicketService.IssuedTicket issued = service.issue(42L);
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        assertNull(service.consume(issued.ticket()));
    }
}
