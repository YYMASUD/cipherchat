package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChatMessage m where m.id = :id")
    Optional<ChatMessage> lockById(UUID id);
    Page<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);
    Optional<ChatMessage> findBySenderIdAndClientId(UUID senderId, UUID clientId);
}