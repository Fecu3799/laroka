package com.laroka.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenBlacklistTest {

	private TokenBlacklist blacklist;

	@BeforeEach
	void setUp() {
		blacklist = new TokenBlacklist(3_600_000L);
	}

	@Test
	void tokenEnBlacklist_esDetectado() {
		blacklist.add("token-revocado");

		assertThat(blacklist.contains("token-revocado")).isTrue();
	}

	@Test
	void tokenValidoNoEnBlacklist_noEsDetectado() {
		assertThat(blacklist.contains("token-valido")).isFalse();
	}

	@Test
	void dosTokensDistintos_tienenEntradasIndependientes() {
		blacklist.add("token-a");

		assertThat(blacklist.contains("token-a")).isTrue();
		assertThat(blacklist.contains("token-b")).isFalse();
	}
}
