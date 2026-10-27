package com.cloudzone.chatapp.controller;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController @RequestMapping("/api/reactions")
public class ReactionController {
    private final ReactionService reactions;
    public ReactionController(ReactionService reactions) { this.reactions = reactions; }
    @PostMapping
    public Api.Message toggle(Principal p, @Valid @RequestBody Api.ToggleReaction request) {
        return reactions.toggle(UUID.fromString(p.getName()), request);
    }
}