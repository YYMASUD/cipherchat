package com.cloudzone.chatapp;

import com.cloudzone.chatapp.dto.Api;
import com.cloudzone.chatapp.security.TokenService;
import com.cloudzone.chatapp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketTests {
    @LocalServerPort int port;
    @Autowired TokenService tokens;
    @Autowired EventService events;
    @Autowired PresenceService presence;
    WebSocketStompClient client;
    @BeforeEach void setup() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
    }
    @AfterEach void cleanup() { client.stop(); }
    StompSession connect(UUID user) throws Exception {
        StompHeaders headers = new StompHeaders(); headers.set("Authorization", "Bearer " + tokens.issue(user));
        return client.connectAsync("ws://localhost:" + port + "/ws", new org.springframework.web.socket.WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }
    @Test void authenticatedPrivateEventsAreDeliveredAndPresenceTracksSession() throws Exception {
        UUID user = UUID.randomUUID();
        StompSession session = connect(user);
        assertThat(presence.online(user)).isTrue();
        BlockingQueue<Api.Event> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/events", new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return Api.Event.class; }
            public void handleFrame(StompHeaders headers, Object payload) { received.add((Api.Event) payload); }
        });
        // Repeated publication avoids relying on timing of asynchronous SUBSCRIBE handling.
        Api.Event event = null;
        for (int i = 0; i < 20 && event == null; i++) {
            events.now(user, "test", Map.of("safe", true));
            event = received.poll(100, TimeUnit.MILLISECONDS);
        }
        assertThat(event).isNotNull(); assertThat(event.type()).isEqualTo("test");
        session.disconnect();
        for (int i = 0; i < 30 && presence.online(user); i++) Thread.sleep(50);
        assertThat(presence.online(user)).isFalse();
    }
    @Test void unauthenticatedConnectIsRejected() {
        assertThatThrownBy(() -> client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(3, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }
    @Test void arbitraryBrokerSubscriptionsAreRejected() throws Exception {
        CountDownLatch error = new CountDownLatch(1);
        StompHeaders headers = new StompHeaders(); headers.set("Authorization", "Bearer " + tokens.issue(UUID.randomUUID()));
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new org.springframework.web.socket.WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
            @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
            @Override public void handleFrame(StompHeaders h, Object p) { error.countDown(); }
            @Override public void handleTransportError(StompSession s, Throwable e) { error.countDown(); }
        }).get(5, TimeUnit.SECONDS);
        session.subscribe("/queue/another-users-events", new StompFrameHandler() {
            public Type getPayloadType(StompHeaders h) { return byte[].class; }
            public void handleFrame(StompHeaders h, Object p) { fail("Unauthorized subscription received a message"); }
        });
        assertThat(error.await(5, TimeUnit.SECONDS)).isTrue();
    }
}