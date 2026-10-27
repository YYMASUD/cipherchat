package com.cloudzone.chatapp.controller;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
import java.util.List;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final RateLimiter limits;
    public AuthController(AuthService auth, RateLimiter limits) { this.auth = auth; this.limits = limits; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Api.Tokens register(@Valid @RequestBody Api.Register request, HttpServletRequest http) {
        limits.check("auth:" + http.getRemoteAddr(), 20); return auth.register(request);
    }
    @PostMapping("/login")
    public Api.Tokens login(@Valid @RequestBody Api.Login request, HttpServletRequest http) {
        limits.check("auth:" + http.getRemoteAddr(), 20); return auth.login(request);
    }
    @PostMapping("/refresh")
    public Api.Tokens refresh(@Valid @RequestBody Api.Refresh request, HttpServletRequest http) {
        limits.check("refresh:" + http.getRemoteAddr(), 120); return auth.rotate(request.refreshToken());
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody Api.Refresh request) { auth.logout(request.refreshToken()); }
    @GetMapping("/sessions")
    public List<Api.SessionView> sessions(Principal p) { return auth.sessions(UUID.fromString(p.getName())); }
    @DeleteMapping("/sessions/{sessionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(Principal p, @PathVariable String sessionId) {
        auth.revokeSession(UUID.fromString(p.getName()), sessionId);
    }
    @DeleteMapping("/sessions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(Principal p) { auth.revokeAllSessions(UUID.fromString(p.getName())); }
    @PostMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Principal p, @Valid @RequestBody Api.ChangePassword request) {
        UUID user = UUID.fromString(p.getName());
        limits.check("password:" + user, 5);
        auth.changePassword(user, request);
    }
}