package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.Conversation;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByFirstUserAndSecondUser(UUID first, UUID second);
    @Query("select c from Conversation c where c.firstUser = :user or c.secondUser = :user order by c.createdAt desc, c.id desc")
    List<Conversation> forUser(UUID user, Pageable pageable);
}