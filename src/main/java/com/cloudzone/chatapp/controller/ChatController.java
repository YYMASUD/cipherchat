package com.cloudzone.chatapp.controller;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/conversations")
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat = chat; }
    private UUID id(Principal p) { return UUID.fromString(p.getName()); }
    @GetMapping public List<Api.ConversationView> list(Principal p, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return chat.list(id(p), page);
    }
    @PostMapping public Api.ConversationView start(Principal p, @Valid @RequestBody Api.StartConversation request) {
        return chat.start(id(p), request.userId());
    }
    @GetMapping("/{conversation}/messages")
    public Api.History history(Principal p, @PathVariable UUID conversation, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return chat.history(id(p), conversation, page);
    }
    @PostMapping("/{conversation}/messages")
    public Api.Message send(Principal p, @PathVariable UUID conversation, @Valid @RequestBody Api.SendMessage request) {
        return chat.send(id(p), conversation, request);
    }
    @PostMapping("/receipts")
    public Api.Message receipt(Principal p, @Valid @RequestBody Api.Receipt request) { return chat.receipt(id(p), request); }
    @MessageMapping("/typing") public void typing(Principal p, @Valid Api.Typing request) { chat.typing(id(p), request); }
    @MessageExceptionHandler
    @SendToUser("/queue/events")
    public Api.Event socketError(Exception e) { return new Api.Event("error", Map.of("message", "Invalid or unauthorized realtime request")); }
}