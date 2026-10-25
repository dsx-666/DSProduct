package com.product.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class RealtimeMessagingConfig {
    public static final String EXCHANGE = "dsproduct.data-change";
    public static final String ROUTING_KEY = "data.changed";

    @Bean
    public TopicExchange realtimeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue realtimeUpdatesQueue() {
        // 每个应用实例都消费一份事件，才能覆盖连接在不同实例上的 WebSocket。
        // 不使用 AnonymousQueue：它会携带新版 RabbitMQ 已禁用的
        // x-queue-master-locator 参数，导致服务端以 541 关闭连接。
        return new Queue(
                "dsproduct.realtime." + UUID.randomUUID(),
                false,
                true,
                true
        );
    }

    @Bean
    public Binding realtimeUpdatesBinding(
            Queue realtimeUpdatesQueue,
            TopicExchange realtimeExchange) {
        return BindingBuilder.bind(realtimeUpdatesQueue)
                .to(realtimeExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
