package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.entity.*;
import com.cloudzone.chatapp.exception.ApiException;
import com.cloudzone.chatapp.repository.*;
import com.cloudzone.chatapp.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refresh;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final long refreshTtl;
    private final String dummyHash;
    public AuthService(UserRepository users, RefreshTokenRepository refresh, PasswordEncoder passwords,
                       TokenService tokens, @Value("${app.jwt.refresh-token-ttl}") long refreshTtl) {
        this.users = users; this.refresh = refresh; this.passwords = passwords; this.tokens = tokens;
        if (refreshTtl <= 0) throw new IllegalArgumentException("Refresh TTL must be positive");
        this.refreshTtl = refreshTtl;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public static Api.User view(ChatUser u) { return new Api.User(u.getId(), u.getUsername(), u.getPublicKey()); }
    @Transactional
    public Api.Tokens register(Api.Register request) {
        validatePassword(request.password());
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(request.publicKey())));
            if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 4096)
                throw new IllegalArgumentException();
        } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "A valid 2048–4096 bit RSA public key is required"); }
        ChatUser user = new ChatUser();
        user.setUsername(request.username().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwords.encode(request.password()));
        user.setPublicKey(request.publicKey());
        users.saveAndFlush(user);
        return issue(user);
    }
    @Transactional
    public Api.Tokens login(Api.Login request) {
        validatePassword(request.password());
        ChatUser user = users.findByUsername(request.username().toLowerCase(Locale.ROOT)).orElse(null);
        boolean valid = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!valid || user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return issue(user);
    }
    @Transactional
    public Api.Tokens rotate(String raw) {
        RefreshToken old = refresh.lockByHash(hash(raw)).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!old.getExpiresAt().isAfter(Instant.now())) throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        refresh.delete(old);
        return issue(users.findById(old.getUserId()).orElseThrow());
    }
    @Transactional
    public void logout(String raw) { refresh.lockByHash(hash(raw)).ifPresent(refresh::delete); }
    private Api.Tokens issue(ChatUser user) {
        byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken t = new RefreshToken(); t.setTokenHash(hash(raw)); t.setUserId(user.getId());
        t.setExpiresAt(Instant.now().plusMillis(refreshTtl)); refresh.save(t);
        return new Api.Tokens(tokens.issue(user.getId()), raw, view(user));
    }
    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
    }
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanExpired() { refresh.deleteByExpiresAtBefore(Instant.now()); }
}