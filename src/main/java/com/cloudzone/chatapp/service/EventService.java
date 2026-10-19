package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.dto.Api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import jakarta.annotation.*;
import java.util.UUID;

@Service
public class EventService {
    public record Dispatch(UUID userId, Api.Event event) {}
    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean shared;
    private RedisMessageListenerContainer listener;
    public EventService(ApplicationEventPublisher publisher, SimpMessagingTemplate messaging,
                        StringRedisTemplate redis, ObjectMapper json,
                        @Value("${app.realtime.redis-enabled:false}") boolean shared) {
        this.publisher = publisher; this.messaging = messaging; this.redis = redis; this.json = json; this.shared = shared;
    }
    @PostConstruct void subscribe() {
        if (!shared) return;
        listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(redis.getConnectionFactory());
        listener.addMessageListener((message, pattern) -> {
            try { local(json.readValue(message.getBody(), Dispatch.class)); }
            catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Invalid realtime event discarded"); }
        }, new ChannelTopic("chat:events"));
        listener.afterPropertiesSet(); listener.start();
    }
    @PreDestroy void stop() throws Exception { if (listener != null) listener.destroy(); }
    public void afterCommit(UUID user, String type, Object data) {
        publisher.publishEvent(new Dispatch(user, new Api.Event(type, data)));
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(Dispatch event) { dispatch(event); }
    public void now(UUID user, String type, Object data) { dispatch(new Dispatch(user, new Api.Event(type, data))); }
    private void dispatch(Dispatch event) {
        if (!shared) { local(event); return; }
        try { redis.convertAndSend("chat:events", json.writeValueAsString(event)); }
        catch (Exception e) {
            // Persistence is authoritative: reconnect/history recovers missed events.
            local(event);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Redis unavailable; event delivered locally only");
        }
    }
    private void local(Dispatch event) { messaging.convertAndSendToUser(event.userId().toString(), "/queue/events", event.event()); }
}