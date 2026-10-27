package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"sender_id", "client_id"}))
@Getter @Setter @NoArgsConstructor
public class ChatMessage {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID conversationId;
    @Column(name = "sender_id", nullable = false) private UUID senderId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(nullable = false, columnDefinition = "text") private String ciphertext;
    @Column(nullable = false, length = 24) private String iv;
    @Column(nullable = false, length = 1024) private String recipientKey;
    @Column(nullable = false, length = 1024) private String senderKey;
    @Column(nullable = false) private Instant createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    private Instant deliveredAt;
    private Instant readAt;
    @Column(nullable = false) private int envelopeVersion = 1;
    private UUID recipientId;
}