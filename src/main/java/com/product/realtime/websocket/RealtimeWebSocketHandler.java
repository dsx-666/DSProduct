package com.product.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.product.realtime.event.DataChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;
    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws IOException {
        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT
                );
        sessions.put(session.getId(), concurrentSession);
        send(concurrentSession, Map.of(
                "type", "CONNECTED",
                "connectedAt", Instant.now()
        ));
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws IOException {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        if ("PING".equals(payload.path("type").asText())) {
            WebSocketSession target = sessions.get(session.getId());
            if (target != null) {
                send(target, Map.of("type", "PONG", "at", Instant.now()));
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception) throws Exception {
        sessions.remove(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        log.debug("WebSocket 连接异常关闭, sessionId={}",
                session.getId(), exception);
    }

    public void broadcast(DataChangeEvent event) {
        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen()) {
                sessions.remove(sessionId);
                return;
            }
            try {
                send(session, Map.of(
                        "type", "DATA_CHANGED",
                        "event", event
                ));
            } catch (IOException | RuntimeException exception) {
                sessions.remove(sessionId);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // 连接已经不可用，无需再次处理。
                }
                log.debug("实时事件发送失败, sessionId={}, eventId={}",
                        sessionId, event.eventId(), exception);
            }
        });
    }

    private void send(WebSocketSession session, Object payload)
            throws IOException {
        session.sendMessage(new TextMessage(
                objectMapper.writeValueAsString(payload)
        ));
    }
}
