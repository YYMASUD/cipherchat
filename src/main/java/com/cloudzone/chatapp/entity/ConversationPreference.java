package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "conversation_preferences")
@Getter @Setter @NoArgsConstructor
public class ConversationPreference {
    @Id @Column(length = 73) private String id;
    @Column(nullable = false) private UUID conversationId;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private boolean pinned;
    @Column(nullable = false) private boolean archived;
    @Column(nullable = false) private boolean muted;
    private Instant lastReadAt;
    private UUID lastReadId;
}