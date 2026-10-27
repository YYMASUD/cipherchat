package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, String> {
    List<MessageReaction> findByMessageId(UUID messageId);
    List<MessageReaction> findByMessageIdIn(List<UUID> messageIds);
}