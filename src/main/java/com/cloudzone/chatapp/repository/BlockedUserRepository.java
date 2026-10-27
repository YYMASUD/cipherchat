package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, String> {}