package com.cloudzone.chatapp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;

@Service
public class TokenService {
    private final SecretKey key;
    private final String issuer;
    private final long ttl;
    public TokenService(@Value("${app.jwt.secret}") String secret,
                        @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.access-token-ttl}") long ttl) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.issuer = issuer;
        if (ttl <= 0) throw new IllegalArgumentException("Access token TTL must be positive");
        this.ttl = ttl;
    }
    public String issue(UUID user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.toString()).issuer(issuer).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttl))).signWith(key, Jwts.SIG.HS256).compact();
    }
    public Claims claims(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();
    }
    public UUID user(String token) { return UUID.fromString(claims(token).getSubject()); }
}