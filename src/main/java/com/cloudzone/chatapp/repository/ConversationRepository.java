package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.Conversation;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> lockById(UUID id);
    Optional<Conversation> findByFirstUserAndSecondUser(UUID first, UUID second);
    @Query("select c from Conversation c where c.firstUser = :user or c.secondUser = :user order by coalesce((select max(m.createdAt) from ChatMessage m where m.conversationId = c.id), c.createdAt) desc, c.id desc")
    List<Conversation> forUser(UUID user, Pageable pageable);
}