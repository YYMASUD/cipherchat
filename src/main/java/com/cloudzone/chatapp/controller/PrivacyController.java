package com.cloudzone.chatapp.controller;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.service.PrivacyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController @RequestMapping("/api/privacy")
public class PrivacyController {
    private final PrivacyService privacy;
    public PrivacyController(PrivacyService privacy) { this.privacy = privacy; }
    @GetMapping public Api.Privacy get(Principal p) { return privacy.get(UUID.fromString(p.getName())); }
    @PostMapping public Api.Privacy update(Principal p, @Valid @RequestBody Api.Privacy request) {
        return privacy.update(UUID.fromString(p.getName()), request);
    }
    @PostMapping("/blocks/{other}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(Principal p, @PathVariable UUID other, @Valid @RequestBody Api.Block request) {
        privacy.block(UUID.fromString(p.getName()), other, request.blocked());
    }
}