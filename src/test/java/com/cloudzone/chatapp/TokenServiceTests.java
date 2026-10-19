package com.cloudzone.chatapp;

import com.cloudzone.chatapp.security.TokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TokenServiceTests {
    private static final byte[] SECRET = new byte[32];
    private static final String ENCODED = Base64.getEncoder().encodeToString(SECRET);
    @Test void validTokenResolvesSubject() {
        TokenService tokens = new TokenService(ENCODED, "test", 60000);
        UUID user = UUID.randomUUID();
        assertThat(tokens.user(tokens.issue(user))).isEqualTo(user);
    }
    @Test void expiredTokenAndWrongIssuerAreRejected() {
        TokenService tokens = new TokenService(ENCODED, "test", 60000);
        String expired = Jwts.builder().subject(UUID.randomUUID().toString()).issuer("test")
                .expiration(Date.from(Instant.now().minusSeconds(1))).signWith(Keys.hmacShaKeyFor(SECRET)).compact();
        assertThatThrownBy(() -> tokens.user(expired)).isInstanceOf(ExpiredJwtException.class);
        String otherIssuer = new TokenService(ENCODED, "wrong", 60000).issue(UUID.randomUUID());
        assertThatThrownBy(() -> tokens.user(otherIssuer)).isInstanceOf(IncorrectClaimException.class);
        byte[] other = new byte[32]; Arrays.fill(other, (byte) 1);
        String otherSignature = new TokenService(Base64.getEncoder().encodeToString(other), "test", 60000).issue(UUID.randomUUID());
        assertThatThrownBy(() -> tokens.user(otherSignature)).isInstanceOf(JwtException.class);
    }
}