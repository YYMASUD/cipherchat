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
    public record User(UUID id, String username, String publicKey) {}
    public record Tokens(String accessToken, String refreshToken, User user) {}
    public record StartConversation(@NotNull UUID userId) {}
    public record ConversationView(UUID id, User peer, Instant createdAt, boolean online) {}
    public record SendMessage(@NotNull UUID clientId,
                              @NotBlank @Size(max = 65536) String ciphertext,
                              @NotBlank @Size(max = 24) String iv,
                              @NotBlank @Size(max = 1024) String recipientKey,
                              @NotBlank @Size(max = 1024) String senderKey) {}
    public record Message(UUID id, UUID conversationId, UUID senderId, UUID clientId,
                          String ciphertext, String iv, String recipientKey, String senderKey,
                          Instant createdAt, Instant deliveredAt, Instant readAt) {}
    public record History(List<Message> messages, boolean hasMore) {}
    public record Receipt(@NotNull UUID messageId, boolean read) {}
    public record Typing(@NotNull UUID conversationId, boolean typing) {}
    public record Event(String type, Object data) {}
}