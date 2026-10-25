package com.product.realtime.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataChangeEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishUserChanged(String action, Long userId, Long actorUserId) {
        applicationEventPublisher.publishEvent(new DataChangeEvent(
                UUID.randomUUID().toString(),
                DataChangeEvent.USERS_CHANGED,
                action,
                userId,
                actorUserId,
                Instant.now()
        ));
    }
}
