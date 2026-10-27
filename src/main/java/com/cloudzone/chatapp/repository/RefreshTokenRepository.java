package com.cloudzone.chatapp.repository;

import com.cloudzone.chatapp.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> lockByHash(String hash);
    long deleteByExpiresAtBefore(Instant now);
    long deleteByUserId(UUID userId);
    List<RefreshToken> findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(UUID userId, Instant now);
    Optional<RefreshToken> findByUserIdAndSessionId(UUID userId, String sessionId);
}