package com.cloudzone.chatapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: simply verifies that the Spring application context starts up.
 *
 * Uses the {@code test} profile, which runs against in-memory H2 in
 * PostgreSQL-compatibility mode so no Docker/PostgreSQL is required.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
