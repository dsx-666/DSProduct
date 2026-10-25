package com.product.realtime.messaging;

import com.product.realtime.event.DataChangeEvent;
import com.product.realtime.websocket.RealtimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataChangeMessageConsumer {
    private final RealtimeWebSocketHandler webSocketHandler;

    @RabbitListener(queues = "#{realtimeUpdatesQueue.name}")
    public void consume(DataChangeEvent event) {
        webSocketHandler.broadcast(event);
    }
}
