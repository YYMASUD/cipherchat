package com.cloudzone.chatapp.config;

import com.cloudzone.chatapp.security.TokenService;
import com.cloudzone.chatapp.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final TokenService tokens;
    private final PresenceService presence;
    private final RateLimiter limits;
    private final String[] origins;
    private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final Map<String, Instant> expiry = new ConcurrentHashMap<>();
    public WebSocketConfig(TokenService tokens, PresenceService presence, RateLimiter limits,
                           @Value("${app.cors.allowed-origins}") String origins) {
        this.tokens = tokens; this.presence = presence; this.limits = limits;
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new);
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) { registry.addEndpoint("/ws").setAllowedOrigins(origins); }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue"); registry.setApplicationDestinationPrefixes("/app"); registry.setUserDestinationPrefix("/user");
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(8192).setSendBufferSizeLimit(262144).setSendTimeLimit(15000).setTimeToFirstMessage(10000);
        registry.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sockets.put(session.getId(), session); expiry.put(session.getId(), Instant.now().plusSeconds(10));
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId()); expiry.remove(session.getId()); presence.disconnect(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (h == null) throw new MessagingException("Missing STOMP headers");
                StompCommand cmd = h.getCommand();
                if (cmd == StompCommand.CONNECT) {
                    String bearer = h.getFirstNativeHeader("Authorization");
                    if (bearer == null || !bearer.startsWith("Bearer ")) throw new MessagingException("Authentication required");
                    try {
                        var claims = tokens.claims(bearer.substring(7));
                        UUID user = UUID.fromString(claims.getSubject());
                        limits.check("ws:" + user, 60);
                        Instant until = claims.getExpiration().toInstant();
                        h.setUser(new UsernamePasswordAuthenticationToken(user.toString(), null, List.of()));
                        expiry.put(h.getSessionId(), until);
                        presence.connect(h.getSessionId(), user, until);
                    } catch (RuntimeException e) { throw new MessagingException("Invalid credentials"); }
                } else if (cmd != StompCommand.DISCONNECT) {
                    if (h.getUser() == null || !expiry.getOrDefault(h.getSessionId(), Instant.MIN).isAfter(Instant.now()))
                        throw new MessagingException("Session expired");
                    limits.check("frame:" + h.getUser().getName(), 300);
                    if (cmd == StompCommand.SUBSCRIBE && !"/user/queue/events".equals(h.getDestination()))
                        throw new MessagingException("Subscription forbidden");
                    if (cmd == StompCommand.SEND && !"/app/typing".equals(h.getDestination()))
                        throw new MessagingException("Destination forbidden");
                    if (cmd != null && cmd != StompCommand.SUBSCRIBE && cmd != StompCommand.SEND && cmd != StompCommand.UNSUBSCRIBE)
                        throw new MessagingException("Unsupported command");
                }
                return message;
            }
        });
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) { presence.disconnect(event.getSessionId()); }
    @Scheduled(fixedDelay = 1000) public void closeExpired() {
        expiry.forEach((id, until) -> {
            if (!until.isAfter(Instant.now())) {
                WebSocketSession session = sockets.get(id);
                if (session != null) try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
            }
        });
    }
}