package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChatMessage m where m.id = :id")
    Optional<ChatMessage> lockById(UUID id);
    Page<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);
    Optional<ChatMessage> findBySenderIdAndClientId(UUID senderId, UUID clientId);
    Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);
    @Query("select m from ChatMessage m where m.conversationId = :conversation and (m.createdAt < :time or (m.createdAt = :time and m.id < :id)) order by m.createdAt desc, m.id desc")
    List<ChatMessage> before(UUID conversation, Instant time, UUID id, Pageable pageable);
    long countByConversationIdAndSenderIdNot(UUID conversation, UUID sender);
    @Query("select count(m) from ChatMessage m where m.conversationId = :conversation and m.senderId <> :user and (m.createdAt > :time or (m.createdAt = :time and m.id > :id))")
    long unreadAfter(UUID conversation, UUID user, Instant time, UUID id);
}