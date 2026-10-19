package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.entity.*;
import com.cloudzone.chatapp.exception.ApiException;
import com.cloudzone.chatapp.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ChatService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final EventService events;
    private final PresenceService presence;
    private final RateLimiter limits;
    private final int maxPayload;
    public ChatService(ConversationRepository conversations, MessageRepository messages, UserRepository users,
                       EventService events, PresenceService presence, RateLimiter limits,
                       @Value("${app.messaging.max-payload-chars}") int maxPayload) {
        this.conversations = conversations; this.messages = messages; this.users = users;
        this.events = events; this.presence = presence; this.limits = limits; this.maxPayload = maxPayload;
    }
    public Conversation member(UUID conversation, UUID user) {
        Conversation c = conversations.findById(conversation).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!c.getFirstUser().equals(user) && !c.getSecondUser().equals(user))
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a conversation participant");
        return c;
    }
    public UUID peer(Conversation c, UUID user) { return c.getFirstUser().equals(user) ? c.getSecondUser() : c.getFirstUser(); }
    public Api.ConversationView start(UUID user, UUID other) {
        limits.check("start:" + user, 30);
        if (user.equals(other)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose another user");
        if (!users.existsById(other)) throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        UUID first = user.toString().compareTo(other.toString()) < 0 ? user : other;
        UUID second = first.equals(user) ? other : user;
        Conversation c = conversations.findByFirstUserAndSecondUser(first, second).orElseGet(() -> {
            Conversation created = new Conversation(); created.setFirstUser(first); created.setSecondUser(second);
            return conversations.saveAndFlush(created);
        });
        events.afterCommit(other, "conversation", view(c, other));
        return view(c, user);
    }
    @Transactional(readOnly = true)
    public List<Api.ConversationView> list(UUID user, int page) {
        return conversations.forUser(user, PageRequest.of(page, 50)).stream().map(c -> view(c, user)).toList();
    }
    private Api.ConversationView view(Conversation c, UUID user) {
        UUID other = peer(c, user);
        return new Api.ConversationView(c.getId(), AuthService.view(users.findById(other).orElseThrow()), c.getCreatedAt(), presence.online(other));
    }
    @Transactional(readOnly = true)
    public Api.History history(UUID user, UUID conversation, int page) {
        member(conversation, user);
        var result = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversation, PageRequest.of(page, 50));
        return new Api.History(result.getContent().stream().map(ChatService::view).toList(), result.hasNext());
    }
    public Api.Message send(UUID user, UUID conversation, Api.SendMessage request) {
        Conversation c = member(conversation, user);
        limits.check("send:" + user, 120);
        ChatMessage duplicate = messages.findBySenderIdAndClientId(user, request.clientId()).orElse(null);
        if (duplicate != null) {
            if (!duplicate.getConversationId().equals(conversation)) throw new ApiException(HttpStatus.CONFLICT, "Client ID already used");
            return view(duplicate);
        }
        if (request.ciphertext().length() > maxPayload) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Encrypted message too large");
        int cipherLength = decode(request.ciphertext()).length;
        if (cipherLength < 16 || decode(request.iv()).length != 12) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid AES-GCM payload");
        validateWrapped(request.recipientKey()); validateWrapped(request.senderKey());
        ChatMessage m = new ChatMessage(); m.setConversationId(conversation); m.setSenderId(user);
        m.setClientId(request.clientId()); m.setCiphertext(request.ciphertext()); m.setIv(request.iv());
        m.setRecipientKey(request.recipientKey()); m.setSenderKey(request.senderKey());
        messages.saveAndFlush(m);
        Api.Message result = view(m);
        events.afterCommit(peer(c, user), "message", result); events.afterCommit(user, "message", result);
        return result;
    }
    public Api.Message receipt(UUID user, Api.Receipt request) {
        limits.check("receipt:" + user, 1000);
        ChatMessage m = messages.lockById(request.messageId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        member(m.getConversationId(), user);
        if (m.getSenderId().equals(user)) throw new ApiException(HttpStatus.FORBIDDEN, "Only the recipient can acknowledge a message");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (m.getDeliveredAt() == null) m.setDeliveredAt(now);
        if (request.read() && m.getReadAt() == null) m.setReadAt(now);
        Api.Message result = view(m);
        events.afterCommit(m.getSenderId(), "receipt", result);
        events.afterCommit(user, "receipt", result);
        return result;
    }
    public void typing(UUID user, Api.Typing request) {
        limits.check("typing:" + user, 120);
        Conversation c = member(request.conversationId(), user);
        events.afterCommit(peer(c, user), "typing", Map.of("conversationId", c.getId(), "userId", user, "typing", request.typing()));
    }
    private static byte[] decode(String value) {
        try { return Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Base64 payload"); }
    }
    private static void validateWrapped(String key) {
        int n = decode(key).length;
        if (n < 256 || n > 512) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid wrapped key");
    }
    public static Api.Message view(ChatMessage m) {
        return new Api.Message(m.getId(), m.getConversationId(), m.getSenderId(), m.getClientId(), m.getCiphertext(),
                m.getIv(), m.getRecipientKey(), m.getSenderKey(), m.getCreatedAt(), m.getDeliveredAt(), m.getReadAt());
    }
}