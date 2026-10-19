package com.cloudzone.chatapp.controller;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.exception.ApiException;
import com.cloudzone.chatapp.repository.UserRepository;
import com.cloudzone.chatapp.service.AuthService;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    public UserController(UserRepository users) { this.users = users; }
    @GetMapping("/me") public Api.User me(Principal p) { return get(UUID.fromString(p.getName())); }
    @GetMapping public List<Api.User> search(@RequestParam @Size(min = 1, max = 32) String q, Principal p) {
        return users.findByUsernameStartingWithAndIdNotOrderByUsername(q.toLowerCase(Locale.ROOT),
                UUID.fromString(p.getName()), PageRequest.of(0, 20)).stream().map(AuthService::view).toList();
    }
    @GetMapping("/{id}/public-key") public Api.User get(@PathVariable UUID id) {
        return AuthService.view(users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found")));
    }
}