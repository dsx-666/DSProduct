package com.product.realtime.messaging;

import com.product.realtime.config.RealtimeMessagingConfig;
import com.product.realtime.event.DataChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataChangeEventRelay {
    private final RabbitTemplate rabbitTemplate;

    @Async("realtimeEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void relay(DataChangeEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RealtimeMessagingConfig.EXCHANGE,
                    RealtimeMessagingConfig.ROUTING_KEY,
                    event
            );
        } catch (RuntimeException exception) {
            log.error("数据更新事件投递 MQ 失败, eventId={}",
                    event.eventId(), exception);
        }
    }
}
