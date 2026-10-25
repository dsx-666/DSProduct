package com.product.realtime.messaging;

import com.product.realtime.event.DataChangeEvent;
import com.product.realtime.websocket.RealtimeWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataChangeMessageConsumerTest {
    @Mock
    private RealtimeWebSocketHandler webSocketHandler;

    @Test
    void shouldBroadcastConsumedMqEvent() {
        DataChangeEvent event = new DataChangeEvent(
                "event-2", DataChangeEvent.USERS_CHANGED,
                "CREATED", 11L, 21L, Instant.parse("2026-10-25T00:00:00Z")
        );

        new DataChangeMessageConsumer(webSocketHandler).consume(event);

        verify(webSocketHandler).broadcast(event);
    }
}
