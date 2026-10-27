package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.ChatUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface UserRepository extends JpaRepository<ChatUser, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ChatUser u where u.id = :id")
    Optional<ChatUser> lockById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from ChatUser u where u.username = :username")
    Optional<ChatUser> lockByUsername(String username);
    Optional<ChatUser> findByUsername(String username);
    List<ChatUser> findByUsernameStartingWithAndIdNotOrderByUsername(String prefix, UUID id, Pageable pageable);
}