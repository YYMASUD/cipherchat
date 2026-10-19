package com.cloudzone.chatapp;

import com.cloudzone.chatapp.service.RateLimiter;
import com.cloudzone.chatapp.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RateLimiterTests {
    @Test void overQuotaIsRejectedAndKeysAreIndependent() {
        RateLimiter limiter = new RateLimiter();
        limiter.check("alice", 2); limiter.check("alice", 2);
        assertThatThrownBy(() -> limiter.check("alice", 2)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many requests");
        assertThatCode(() -> limiter.check("bob", 2)).doesNotThrowAnyException();
    }
}