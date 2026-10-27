package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"first_user", "second_user"}))
@Getter @Setter @NoArgsConstructor
public class Conversation {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "first_user", nullable = false) private UUID firstUser;
    @Column(name = "second_user", nullable = false) private UUID secondUser;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private UUID requestRecipient;
    @Column(nullable = false, length = 16) private String requestState = "accepted";
}