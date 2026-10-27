package com.cloudzone.chatapp.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class Api {
    private Api() {}
    public record Register(@Pattern(regexp = "[a-zA-Z0-9_]{3,32}") @NotNull String username,
                           @NotNull @Size(min = 12, max = 72) String password,
                           @NotBlank @Size(max = 2048) String publicKey) {}
    public record Login(@NotBlank @Size(max = 32) String username,
                        @NotBlank @Size(max = 72) String password) {}
    public record Refresh(@NotBlank @Size(max = 128) String refreshToken) {}
    public record SessionView(String id, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {}
    public record ChangePassword(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotNull @Size(min = 12, max = 72) String newPassword) {}
    public record User(UUID id, String username, String publicKey) {}
    public record Tokens(String accessToken, String refreshToken, User user) {}
    public record StartConversation(@NotNull UUID userId) {}
    public record ConversationView(UUID id, User peer, Instant createdAt, boolean online,
                                   Instant lastActivityAt, long unreadCount, boolean pinned, boolean archived, boolean muted, boolean blocked,
                                   String requestState, boolean incomingRequest) {}
    public record Privacy(boolean showPresence, boolean sendTyping, boolean sendReadReceipts, boolean requireMessageRequests) {}
    public record RequestDecision(boolean accept) {}
    public record Block(boolean blocked) {}
    public record ConversationSettings(boolean pinned, boolean archived, boolean muted) {}
    public record ReadPosition(@NotNull UUID messageId) {}
    public record SendMessage(@NotNull UUID clientId,
                              @NotBlank @Size(max = 65536) String ciphertext,
                              @NotBlank @Size(max = 24) String iv,
                              @NotBlank @Size(max = 1024) String recipientKey,
                              @NotBlank @Size(max = 1024) String senderKey,
                              @Min(1) @Max(2) Integer envelopeVersion, UUID conversationId, UUID senderId, UUID recipientId) {}
    public record Reaction(String emoji, UUID userId, String username) {}
    public record ToggleReaction(@NotNull UUID messageId, @NotBlank @Size(max = 8) String emoji) {}
    public record Message(UUID id, UUID conversationId, UUID senderId, UUID clientId,
                          String ciphertext, String iv, String recipientKey, String senderKey,
                          Instant createdAt, Instant deliveredAt, Instant readAt, int envelopeVersion, UUID recipientId,
                          Instant deletedAt, List<Reaction> reactions) {}
    public record History(List<Message> messages, boolean hasMore, UUID nextCursor) {}
    public record Receipt(@NotNull UUID messageId, boolean read) {}
    public record Typing(@NotNull UUID conversationId, boolean typing) {}
    public record Event(String type, Object data) {}
}