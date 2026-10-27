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
    private final ConversationPreferenceRepository preferences;
    private final PrivacyService privacy;
    public ChatService(ConversationRepository conversations, MessageRepository messages, UserRepository users,
                       EventService events, PresenceService presence, RateLimiter limits,
                       @Value("${app.messaging.max-payload-chars}") int maxPayload, ConversationPreferenceRepository preferences, PrivacyService privacy) {
        this.conversations = conversations; this.messages = messages; this.users = users;
        this.events = events; this.presence = presence; this.limits = limits; this.maxPayload = maxPayload;
        this.preferences = preferences;
        this.privacy = privacy;
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
        privacy.assertAllowed(user, other);
        UUID first = user.toString().compareTo(other.toString()) < 0 ? user : other;
        UUID second = first.equals(user) ? other : user;
        Conversation c = conversations.findByFirstUserAndSecondUser(first, second).orElseGet(() -> {
            Conversation created = new Conversation(); created.setFirstUser(first); created.setSecondUser(second);
            if (privacy.get(other).requireMessageRequests()) {
                created.setRequestRecipient(other); created.setRequestState("pending");
            }
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
        ConversationPreference pref = preferences.findById(c.getId() + ":" + user).orElse(null);
        long unread = pref == null || pref.getLastReadAt() == null
                ? messages.countByConversationIdAndSenderIdNotAndDeletedAtIsNull(c.getId(), user)
                : messages.unreadAfter(c.getId(), user, pref.getLastReadAt(), pref.getLastReadId());
        Instant activity = messages.findFirstByConversationIdOrderByCreatedAtDescIdDesc(c.getId())
                .map(ChatMessage::getCreatedAt).orElse(c.getCreatedAt());
        return new Api.ConversationView(c.getId(), AuthService.view(users.findById(other).orElseThrow()), c.getCreatedAt(),
                c.getRequestState().equals("accepted") && !privacy.restricted(user, other) && privacy.get(other).showPresence() && presence.online(other),
                activity, unread, pref != null && pref.isPinned(), pref != null && pref.isArchived(), pref != null && pref.isMuted(), privacy.blocked(user, other),
                c.getRequestState(), user.equals(c.getRequestRecipient()));
    }
    // Fix Bug 1 & Bug 3: removed erroneous copy-pasted envelope-validation code (referenced undefined
    // "request" variable), and changed "declined" -> "rejected" to match the DB CHECK constraint.
    public Api.ConversationView decideRequest(UUID user, UUID conversation, boolean accept) {
        member(conversation, user);
        Conversation c = conversations.lockById(conversation).orElseThrow();
        if (!user.equals(c.getRequestRecipient())) throw new ApiException(HttpStatus.FORBIDDEN, "Only the request recipient can decide");
        privacy.assertAllowed(user, peer(c, user));
        if (c.getRequestState().equals("accepted")) {
            // Idempotent re-accept is fine; attempting to decline after acceptance is a conflict.
            if (accept) return view(c, user);
            throw new ApiException(HttpStatus.CONFLICT, "Conversation is already accepted");
        }
        if (c.getRequestState().equals("rejected") && !accept) return view(c, user);
        c.setRequestState(accept ? "accepted" : "rejected");
        events.afterCommit(peer(c, user), "conversation", view(c, peer(c, user)));
        Api.ConversationView result = view(c, user);
        events.afterCommit(user, "conversation", result);
        return result;
    }
    private ConversationPreference preference(UUID conversation, UUID user) {
        member(conversation, user);
        // Serialize preference creation and read-position changes, including across tabs.
        conversations.lockById(conversation).orElseThrow();
        return preferences.findById(conversation + ":" + user).orElseGet(() -> {
            ConversationPreference p = new ConversationPreference();
            p.setId(conversation + ":" + user); p.setConversationId(conversation); p.setUserId(user);
            return preferences.save(p);
        });
    }
    public Api.ConversationView settings(UUID user, UUID conversation, Api.ConversationSettings request) {
        ConversationPreference p = preference(conversation, user);
        p.setPinned(request.pinned()); p.setArchived(request.archived()); p.setMuted(request.muted());
        Api.ConversationView result = view(member(conversation, user), user);
        events.afterCommit(user, "conversation", result);
        return result;
    }
    public Api.ConversationView read(UUID user, UUID conversation, UUID messageId) {
        member(conversation, user);
        ChatMessage m = messages.findById(messageId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!m.getConversationId().equals(conversation)) throw new ApiException(HttpStatus.BAD_REQUEST, "Message belongs to another conversation");
        ConversationPreference p = preference(conversation, user);
        if (p.getLastReadAt() == null || m.getCreatedAt().isAfter(p.getLastReadAt()) ||
                (m.getCreatedAt().equals(p.getLastReadAt()) && m.getId().toString().compareTo(p.getLastReadId().toString()) > 0)) {
            p.setLastReadAt(m.getCreatedAt()); p.setLastReadId(m.getId());
        }
        Api.ConversationView result = view(member(conversation, user), user);
        events.afterCommit(user, "conversation", result);
        return result;
    }
    @Transactional(readOnly = true)
    public Api.History history(UUID user, UUID conversation, int page, UUID before) {
        member(conversation, user);
        if (before != null) {
            ChatMessage anchor = messages.findById(before).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid history cursor"));
            if (!anchor.getConversationId().equals(conversation)) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid history cursor");
            var older = messages.before(conversation, anchor.getCreatedAt(), before, PageRequest.of(0, 51));
            var items = older.stream().limit(50).map(ChatService::view).toList();
            return new Api.History(items, older.size() > 50, items.isEmpty() ? null : items.getLast().id());
        }
        var result = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversation, PageRequest.of(page, 50));
        var items = result.getContent().stream().map(ChatService::view).toList();
        return new Api.History(items, result.hasNext(), items.isEmpty() ? null : items.getLast().id());
    }
    public Api.Message send(UUID user, UUID conversation, Api.SendMessage request) {
        Conversation c = member(conversation, user);
        // Serialize introductions and request decisions across instances.
        // Fix Bug 4: removed duplicate lockById call.
        c = conversations.lockById(conversation).orElseThrow();
        limits.check("send:" + user, 120);
        ChatMessage duplicate = messages.findBySenderIdAndClientId(user, request.clientId()).orElse(null);
        if (duplicate != null) {
            if (!duplicate.getConversationId().equals(conversation)) throw new ApiException(HttpStatus.CONFLICT, "Client ID already used");
            return view(duplicate);
        }
        privacy.assertAllowed(user, peer(c, user));
        if (!c.getRequestState().equals("accepted") && (!c.getRequestState().equals("pending") ||
                user.equals(c.getRequestRecipient()) || messages.countByConversationIdAndSenderIdNot(conversation, c.getRequestRecipient()) > 0))
            throw new ApiException(HttpStatus.FORBIDDEN, "Message request must be accepted before continuing");
        if (request.ciphertext().length() > maxPayload) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Encrypted message too large");
        int cipherLength = decode(request.ciphertext()).length;
        if (cipherLength < 16 || decode(request.iv()).length != 12) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid AES-GCM payload");
        validateWrapped(request.recipientKey()); validateWrapped(request.senderKey());
        // Fix Bug 2: envelopeVersion is now correctly declared and validated inside send().
        int envelopeVersion = request.envelopeVersion() == null ? 1 : request.envelopeVersion();
        if (envelopeVersion == 2 && (request.conversationId() == null || request.senderId() == null || request.recipientId() == null ||
                !conversation.equals(request.conversationId()) || !user.equals(request.senderId()) ||
                !peer(c, user).equals(request.recipientId())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Envelope does not match conversation participants");
        ChatMessage m = new ChatMessage(); m.setConversationId(conversation); m.setSenderId(user);
        m.setClientId(request.clientId()); m.setCiphertext(request.ciphertext()); m.setIv(request.iv());
        m.setRecipientKey(request.recipientKey()); m.setSenderKey(request.senderKey());
        m.setEnvelopeVersion(envelopeVersion); m.setRecipientId(envelopeVersion == 2 ? peer(c, user) : null);
        messages.saveAndFlush(m);
        Api.Message result = view(m);
        events.afterCommit(peer(c, user), "message", result); events.afterCommit(user, "message", result);
        return result;
    }
    public void deleteMessage(UUID user, UUID conversation, UUID messageId) {
        member(conversation, user);
        ChatMessage m = messages.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!m.getConversationId().equals(conversation))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message does not belong to this conversation");
        if (!m.getSenderId().equals(user))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the sender can delete a message");
        if (m.getDeletedAt() != null) return; // already deleted, idempotent
        m.setDeletedAt(Instant.now());
        Api.Message result = view(m);
        UUID peerUser = peer(member(conversation, user), user);
        events.afterCommit(peerUser, "message_deleted", result);
        events.afterCommit(user, "message_deleted", result);
    }
    public Api.Message receipt(UUID user, Api.Receipt request) {
        limits.check("receipt:" + user, 1000);
        ChatMessage m = messages.lockById(request.messageId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        Conversation c = member(m.getConversationId(), user);
        if (m.getSenderId().equals(user)) throw new ApiException(HttpStatus.FORBIDDEN, "Only the recipient can acknowledge a message");
        if (!c.getRequestState().equals("accepted")) return view(m);
        if (privacy.restricted(user, m.getSenderId())) return view(m);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (m.getDeliveredAt() == null) m.setDeliveredAt(now);
        if (request.read() && privacy.get(user).sendReadReceipts() && m.getReadAt() == null) m.setReadAt(now);
        Api.Message result = view(m);
        events.afterCommit(m.getSenderId(), "receipt", result);
        events.afterCommit(user, "receipt", result);
        return result;
    }
    public void typing(UUID user, Api.Typing request) {
        limits.check("typing:" + user, 120);
        Conversation c = member(request.conversationId(), user);
        privacy.assertAllowed(user, peer(c, user));
        if (!c.getRequestState().equals("accepted")) return;
        if (!privacy.get(user).sendTyping()) return;
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
                m.getIv(), m.getRecipientKey(), m.getSenderKey(), m.getCreatedAt(), m.getDeliveredAt(), m.getReadAt(),
                m.getEnvelopeVersion(), m.getRecipientId(), m.getDeletedAt(), null);
    }
}