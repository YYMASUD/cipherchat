package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "message_reactions")
@Getter @Setter @NoArgsConstructor
public class MessageReaction {
    @Id @Column(length = 200) private String id;
    @Column(nullable = false) private UUID messageId;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false, length = 8) private String emoji;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}