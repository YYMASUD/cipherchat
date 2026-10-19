package com.cloudzone.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    private record Session(UUID user, Instant expires) {}
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final boolean shared;
    public PresenceService(StringRedisTemplate redis, @Value("${app.realtime.redis-enabled:false}") boolean shared) {
        this.redis = redis; this.shared = shared;
    }
    public void connect(String session, UUID user, Instant expires) {
        sessions.put(session, new Session(user, expires));
        renew();
    }
    public void disconnect(String session) { sessions.remove(session); }
    public boolean online(UUID user) {
        if (shared) {
            try { return Boolean.TRUE.equals(redis.hasKey("chat:online:" + user)); }
            catch (org.springframework.data.redis.RedisConnectionFailureException ignored) { return false; }
        }
        return sessions.values().stream().anyMatch(s -> s.user.equals(user) && s.expires.isAfter(Instant.now()));
    }
    @Scheduled(fixedDelay = 10000)
    public void renew() {
        sessions.entrySet().removeIf(e -> !e.getValue().expires.isAfter(Instant.now()));
        if (shared) for (UUID user : sessions.values().stream().map(Session::user).distinct().toList()) {
            try { redis.opsForValue().set("chat:online:" + user, "1", Duration.ofSeconds(25)); }
            catch (org.springframework.data.redis.RedisConnectionFailureException ignored) { /* Presence is best effort. */ }
        }
    }
}