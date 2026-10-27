package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.PrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, UUID> {}