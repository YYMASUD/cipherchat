package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.ConversationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationPreferenceRepository extends JpaRepository<ConversationPreference, String> {}