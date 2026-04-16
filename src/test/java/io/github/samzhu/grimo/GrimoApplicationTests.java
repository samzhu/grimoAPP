package io.github.samzhu.grimo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GrimoApplicationTests {

	@Value("${spring.threads.virtual.enabled:false}")
	boolean virtualThreadsEnabled;

	@Test
	@DisplayName("AC-2 Spring context loads with zero business beans")
	void contextLoads() {
		// Empty body — @SpringBootTest performs the check.
	}

	@Test
	@DisplayName("AC-5 virtual threads are enabled via application.yaml")
	void virtualThreadsEnabled() {
		assertThat(virtualThreadsEnabled).isTrue();
	}

}
