package com.product.realtime.messaging;

import com.product.realtime.config.RealtimeMessagingConfig;
import com.product.realtime.event.DataChangeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataChangeEventRelayTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldSendCommittedEventToRabbitMq() {
        DataChangeEvent event = new DataChangeEvent(
                "event-1", DataChangeEvent.USERS_CHANGED,
                "UPDATED", 10L, 20L, Instant.parse("2026-10-25T00:00:00Z")
        );

        new DataChangeEventRelay(rabbitTemplate).relay(event);

        verify(rabbitTemplate).convertAndSend(
                RealtimeMessagingConfig.EXCHANGE,
                RealtimeMessagingConfig.ROUTING_KEY,
                event
        );
    }
}
