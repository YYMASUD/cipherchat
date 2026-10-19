package com.cloudzone.chatapp;

import com.cloudzone.chatapp.repository.*;
import com.cloudzone.chatapp.security.TokenService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.security.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ChatApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MessageRepository messages;
    @Autowired ConversationRepository conversations;
    @Autowired RefreshTokenRepository refresh;
    @Autowired UserRepository users;
    @Autowired TokenService tokenService;
    static String publicKey;
    static final String PASSWORD = "a sufficiently strong password";
    @BeforeAll static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        publicKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    }
    @BeforeEach void clear() {
        messages.deleteAll(); conversations.deleteAll(); refresh.deleteAll(); users.deleteAll();
    }
    JsonNode postJson(String path, Object body, String token, int status) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andExpect(status().is(status)).andReturn();
        return result.getResponse().getContentAsString().isEmpty() ? null : json.readTree(result.getResponse().getContentAsString());
    }
    JsonNode register(String name) throws Exception {
        return postJson("/api/auth/register", Map.of("username", name, "password", PASSWORD, "publicKey", publicKey), null, 201);
    }
    String access(JsonNode auth) { return auth.get("accessToken").asText(); }
    String user(JsonNode auth) { return auth.get("user").get("id").asText(); }
    Map<String, Object> payload() {
        return Map.of("clientId", UUID.randomUUID().toString(), "ciphertext", Base64.getEncoder().encodeToString(new byte[32]),
                "iv", Base64.getEncoder().encodeToString(new byte[12]),
                "senderKey", Base64.getEncoder().encodeToString(new byte[256]),
                "recipientKey", Base64.getEncoder().encodeToString(new byte[256]));
    }
    @Test void registrationLoginRotationLogoutAndRevocation() throws Exception {
        JsonNode alice = register("Alice");
        assertThat(alice.at("/user/username").asText()).isEqualTo("alice");
        assertThat(users.findByUsername("alice").orElseThrow().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(refresh.findAll()).allSatisfy(t -> assertThat(t.getTokenHash()).isNotEqualTo(alice.get("refreshToken").asText()));
        postJson("/api/auth/register", Map.of("username", "ALICE", "password", PASSWORD, "publicKey", publicKey), null, 409);
        postJson("/api/auth/login", Map.of("username", "alice", "password", "wrong password"), null, 401);
        JsonNode login = postJson("/api/auth/login", Map.of("username", "ALICE", "password", PASSWORD), null, 200);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access(login))).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        JsonNode rotated = postJson("/api/auth/refresh", Map.of("refreshToken", login.get("refreshToken").asText()), null, 200);
        postJson("/api/auth/refresh", Map.of("refreshToken", login.get("refreshToken").asText()), null, 401);
        postJson("/api/auth/logout", Map.of("refreshToken", rotated.get("refreshToken").asText()), null, 204);
        postJson("/api/auth/refresh", Map.of("refreshToken", rotated.get("refreshToken").asText()), null, 401);
    }
    @Test void validationAndUnauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        postJson("/api/auth/register", Map.of("username", "bad name", "password", "short", "publicKey", publicKey), null, 400);
        postJson("/api/auth/register", Map.of("username", "validname", "password", PASSWORD, "publicKey", "bad-key"), null, 400);
        postJson("/api/auth/register", Map.of("username", "validname", "password", "🔐".repeat(30), "publicKey", publicKey), null, 400);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void conversationAuthorizationIdempotencyHistoryAndReceipts() throws Exception {
        JsonNode alice = register("alice"), bob = register("bobby"), eve = register("evelyn");
        JsonNode conversation = postJson("/api/conversations", Map.of("userId", user(bob)), access(alice), 200);
        String cid = conversation.get("id").asText();
        assertThat(postJson("/api/conversations", Map.of("userId", user(alice)), access(bob), 200).get("id").asText()).isEqualTo(cid);
        mvc.perform(get("/api/conversations/" + cid + "/messages").header("Authorization", "Bearer " + access(eve)))
                .andExpect(status().isForbidden());
        Map<String, Object> encrypted = payload();
        JsonNode sent = postJson("/api/conversations/" + cid + "/messages", encrypted, access(alice), 200);
        assertThat(postJson("/api/conversations/" + cid + "/messages", encrypted, access(alice), 200).get("id")).isEqualTo(sent.get("id"));
        assertThat(messages.count()).isEqualTo(1);
        mvc.perform(get("/api/conversations/" + cid + "/messages").header("Authorization", "Bearer " + access(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].ciphertext").value(encrypted.get("ciphertext")))
                .andExpect(jsonPath("$.hasMore").value(false));
        Map<String, Object> receipt = Map.of("messageId", sent.get("id").asText(), "read", true);
        postJson("/api/conversations/receipts", receipt, access(eve), 403);
        postJson("/api/conversations/receipts", receipt, access(alice), 403);
        JsonNode read = postJson("/api/conversations/receipts", receipt, access(bob), 200);
        assertThat(read.get("readAt")).isNotNull(); assertThat(read.get("deliveredAt")).isNotNull();
        JsonNode again = postJson("/api/conversations/receipts", Map.of("messageId", sent.get("id").asText(), "read", false), access(bob), 200);
        assertThat(again.get("readAt")).isEqualTo(read.get("readAt"));
        postJson("/api/conversations/" + cid + "/messages", encrypted, access(eve), 403);
        var invalid = new HashMap<>(payload()); invalid.put("iv", "not-base64");
        postJson("/api/conversations/" + cid + "/messages", invalid, access(alice), 400);
        var oversized = new HashMap<>(payload()); oversized.put("ciphertext", "A".repeat(65537));
        postJson("/api/conversations/" + cid + "/messages", oversized, access(alice), 400);
        mvc.perform(get("/api/conversations").param("page", "-1").header("Authorization", "Bearer " + access(alice)))
                .andExpect(status().isBadRequest());
    }
    @Test void searchIsAuthenticatedBoundedAndExcludesSelf() throws Exception {
        JsonNode alice = register("alice"); register("alicia");
        mvc.perform(get("/api/users").param("q", "ali").header("Authorization", "Bearer " + access(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].username").value("alicia"));
    }
    @Test void historyPaginationAndExpiredRefresh() throws Exception {
        JsonNode alice = register("alice"), bob = register("bobby");
        String cid = postJson("/api/conversations", Map.of("userId", user(bob)), access(alice), 200).get("id").asText();
        for (int i = 0; i < 51; i++) {
            com.cloudzone.chatapp.entity.ChatMessage m = new com.cloudzone.chatapp.entity.ChatMessage();
            m.setConversationId(UUID.fromString(cid)); m.setSenderId(UUID.fromString(user(alice))); m.setClientId(UUID.randomUUID());
            m.setCiphertext("encrypted"); m.setIv("iv"); m.setRecipientKey("wrapped"); m.setSenderKey("wrapped");
            messages.save(m);
        }
        mvc.perform(get("/api/conversations/" + cid + "/messages").header("Authorization", "Bearer " + access(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(50)).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/conversations/" + cid + "/messages?page=1").header("Authorization", "Bearer " + access(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.hasMore").value(false));
        var expired = refresh.findAll().stream().filter(t -> t.getUserId().toString().equals(user(alice))).findFirst().orElseThrow();
        expired.setExpiresAt(java.time.Instant.now().minusSeconds(60)); refresh.saveAndFlush(expired);
        postJson("/api/auth/refresh", Map.of("refreshToken", alice.get("refreshToken").asText()), null, 401);
    }
}