package com.product.realtime.websocket;

import com.product.realtime.config.RealtimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RealtimeTicketService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "realtime:ticket:";
    private final StringRedisTemplate redisTemplate;
    private final RealtimeProperties properties;

    public IssuedTicket issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(properties.getTicketTtl());
        redisTemplate.opsForValue().set(
                KEY_PREFIX + value,
                String.valueOf(userId),
                properties.getTicketTtl()
        );
        return new IssuedTicket(value, expiresAt);
    }

    public TicketOwner consume(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String userId = redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + value);
        if (userId == null) {
            return null;
        }
        try {
            return new TicketOwner(Long.valueOf(userId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record IssuedTicket(String ticket, Instant expiresAt) { }

    public record TicketOwner(Long userId) { }
}
