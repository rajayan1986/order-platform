package com.platform.notification.websocket;

import com.platform.notification.registry.SessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OrderStatusHandler implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusHandler.class);
    private static final Pattern TOPIC_ORDERS_PATTERN = Pattern.compile("^/topic/orders/(.+)$");

    private final SessionRegistry sessionRegistry;
    private final Set<String> connectedSessions = ConcurrentHashMap.newKeySet();

    public OrderStatusHandler(SessionRegistry sessionRegistry, MeterRegistry meterRegistry) {
        this.sessionRegistry = sessionRegistry;
        Gauge.builder("websocket.active.connections", connectedSessions, Set::size)
                .register(meterRegistry);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher matcher = TOPIC_ORDERS_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    String orderId = matcher.group(1);
                    String sessionId = accessor.getSessionId();
                    if (sessionId != null) {
                        sessionRegistry.register(sessionId, orderId);
                        connectedSessions.add(sessionId);
                        log.info("Client subscribed to order {} sessionId={}", orderId, sessionId);
                    }
                }
            }
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();
            if (sessionId != null) {
                sessionRegistry.unregister(sessionId);
                connectedSessions.remove(sessionId);
                log.info("Client disconnected sessionId={}", sessionId);
            }
        }
        return message;
    }
}
