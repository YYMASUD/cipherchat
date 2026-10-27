package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.entity.MessageReaction;
import com.cloudzone.chatapp.exception.ApiException;
import com.cloudzone.chatapp.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional
public class ReactionService {
    private static final Set<String> ALLOWED = Set.of("\uD83D\uDC4D","\u2764\uFE0F","\uD83D\uDE02","\uD83D\uDE2E","\uD83D\uDE22","\uD83D\uDD25","\u2705");
    private final MessageReactionRepository reactions;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatService chat;
    private final EventService events;
    private final RateLimiter limits;

    public ReactionService(MessageReactionRepository reactions, MessageRepository messages,
                           UserRepository users, ChatService chat, EventService events, RateLimiter limits) {
        this.reactions = reactions; this.messages = messages; this.users = users;
        this.chat = chat; this.events = events; this.limits = limits;
    }

    public Api.Message toggle(UUID user, Api.ToggleReaction request) {
        limits.check("reaction:" + user, 60);
        if (!ALLOWED.contains(request.emoji()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported emoji");
        var m = messages.findById(request.messageId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        chat.member(m.getConversationId(), user);
        if (m.getDeletedAt() != null)
            throw new ApiException(HttpStatus.GONE, "Cannot react to a deleted message");
        String id = request.messageId() + ":" + user + ":" + request.emoji();
        if (reactions.existsById(id)) {
            reactions.deleteById(id);
        } else {
            MessageReaction r = new MessageReaction();
            r.setId(id); r.setMessageId(request.messageId()); r.setUserId(user); r.setEmoji(request.emoji());
            reactions.save(r);
        }
        reactions.flush();
        Api.Message result = viewWithReactions(request.messageId());
        UUID peer = chat.peer(chat.member(m.getConversationId(), user), user);
        events.afterCommit(peer, "reaction", result);
        events.afterCommit(user, "reaction", result);
        return result;
    }

    @Transactional(readOnly = true)
    public Api.Message viewWithReactions(UUID messageId) {
        var m = messages.findById(messageId).orElseThrow();
        var reactionList = reactions.findByMessageId(messageId).stream()
                .map(r -> {
                    String username = users.findById(r.getUserId()).map(u -> u.getUsername()).orElse("unknown");
                    return new Api.Reaction(r.getEmoji(), r.getUserId(), username);
                }).toList();
        return new Api.Message(m.getId(), m.getConversationId(), m.getSenderId(), m.getClientId(),
                m.getCiphertext(), m.getIv(), m.getRecipientKey(), m.getSenderKey(),
                m.getCreatedAt(), m.getDeliveredAt(), m.getReadAt(), m.getEnvelopeVersion(),
                m.getRecipientId(), m.getDeletedAt(), reactionList);
    }
}