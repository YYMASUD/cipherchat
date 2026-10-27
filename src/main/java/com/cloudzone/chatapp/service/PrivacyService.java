package com.cloudzone.chatapp.service;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.entity.*;
import com.cloudzone.chatapp.exception.ApiException;
import com.cloudzone.chatapp.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @Transactional
public class PrivacyService {
    private final PrivacySettingsRepository settings;
    private final BlockedUserRepository blocks;
    private final UserRepository users;
    public PrivacyService(PrivacySettingsRepository settings, BlockedUserRepository blocks, UserRepository users) {
        this.settings = settings; this.blocks = blocks; this.users = users;
    }
    public Api.Privacy get(UUID user) {
        PrivacySettings p = settings.findById(user).orElseGet(PrivacySettings::new);
        return new Api.Privacy(p.isShowPresence(), p.isSendTyping(), p.isSendReadReceipts(), p.isRequireMessageRequests());
    }
    public Api.Privacy update(UUID user, Api.Privacy request) {
        PrivacySettings p = settings.findById(user).orElseGet(PrivacySettings::new); p.setUserId(user);
        p.setShowPresence(request.showPresence()); p.setSendTyping(request.sendTyping()); p.setSendReadReceipts(request.sendReadReceipts());
        p.setRequireMessageRequests(request.requireMessageRequests());
        settings.save(p); return get(user);
    }
    public boolean blocked(UUID user, UUID other) { return blocks.existsById(user + ":" + other); }
    public boolean restricted(UUID user, UUID other) { return blocked(user, other) || blocked(other, user); }
    public void assertAllowed(UUID user, UUID other) {
        if (restricted(user, other)) throw new ApiException(HttpStatus.FORBIDDEN, "Messaging is unavailable for this contact");
    }
    public void block(UUID user, UUID other, boolean value) {
        if (user.equals(other)) throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot block yourself");
        if (!users.existsById(other)) throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        String id = user + ":" + other;
        if (value && !blocks.existsById(id)) {
            BlockedUser b = new BlockedUser(); b.setId(id); b.setUserId(user); b.setBlockedId(other); blocks.save(b);
        } else if (!value) blocks.findById(id).ifPresent(blocks::delete);
    }
}