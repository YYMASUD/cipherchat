package com.cloudzone.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "privacy_settings")
@Getter @Setter @NoArgsConstructor
public class PrivacySettings {
    @Id private UUID userId;
    @Column(nullable = false) private boolean showPresence = true;
    @Column(nullable = false) private boolean sendTyping = true;
    @Column(nullable = false) private boolean sendReadReceipts = true;
    @Column(nullable = false) private boolean requireMessageRequests;
}