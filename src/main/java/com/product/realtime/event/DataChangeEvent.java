package com.product.realtime.event;

import java.time.Instant;

public record DataChangeEvent(
        String eventId,
        String topic,
        String action,
        Long entityId,
        Long actorUserId,
        Instant occurredAt
) {
    public static final String USERS_CHANGED = "USERS_CHANGED";
}
