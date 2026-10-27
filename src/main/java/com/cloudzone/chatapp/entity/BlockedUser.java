package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "blocked_users")
@Getter @Setter @NoArgsConstructor
public class BlockedUser {
    @Id @Column(length = 73) private String id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID blockedId;
}