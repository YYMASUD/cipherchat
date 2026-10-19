package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Per-instance guard. Deploy behind a shared edge limiter for cluster-wide quotas. */
@Component
public class RateLimiter {
    private record Window(long minute, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public void check(String key, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute < minute);
        if (windows.size() > 20000) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Server busy; retry shortly");
        Window w = windows.compute(key, (k, old) -> old == null || old.minute != minute
                ? new Window(minute, 1) : new Window(minute, old.count + 1));
        if (w.count > limit) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; retry next minute");
    }
}