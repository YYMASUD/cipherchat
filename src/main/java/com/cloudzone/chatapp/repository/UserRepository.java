package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.ChatUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<ChatUser, UUID> {
    Optional<ChatUser> findByUsername(String username);
    List<ChatUser> findByUsernameStartingWithAndIdNotOrderByUsername(String prefix, UUID id, Pageable pageable);
}