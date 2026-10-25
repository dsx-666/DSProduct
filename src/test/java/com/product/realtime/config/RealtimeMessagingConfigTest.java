package com.product.realtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeMessagingConfigTest {
    @Test
    void shouldCreateCompatiblePerInstanceQueueWithoutDeprecatedArguments() {
        Queue queue = new RealtimeMessagingConfig().realtimeUpdatesQueue();

        assertTrue(queue.getName().startsWith("dsproduct.realtime."));
        assertFalse(queue.isDurable());
        assertTrue(queue.isExclusive());
        assertTrue(queue.isAutoDelete());
        assertFalse(queue.getArguments().containsKey("x-queue-master-locator"));
        assertFalse(queue.getArguments().containsKey("x-queue-leader-locator"));
    }
}
