package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id @Column(length = 64) private String tokenHash;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false, length = 64) private String sessionId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant lastUsedAt;
}