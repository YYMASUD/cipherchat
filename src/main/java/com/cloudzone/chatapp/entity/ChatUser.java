package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_users")
@Getter @Setter @NoArgsConstructor
public class ChatUser {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 32) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, columnDefinition = "text") private String publicKey;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}