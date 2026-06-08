package com.laroka.backend.shared.security;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * In-memory blacklist for revoked JWT tokens, backed by Caffeine.
 * Entries expire automatically after JWT_EXPIRATION milliseconds, matching
 * the maximum remaining validity of any token added at logout time.
 *
 * The blacklist is NOT persisted: a server restart clears all entries.
 * Tokens that were invalidated before the restart will again be accepted
 * until they expire naturally via their JWT expiry claim. This is an
 * accepted trade-off for a stateless, single-instance deployment.
 */
@Component
public class TokenBlacklist {

	private final Cache<String, Boolean> cache;

	public TokenBlacklist(@Value("${jwt.expiration}") long expirationMs) {
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(expirationMs, TimeUnit.MILLISECONDS)
				.build();
	}

	public void add(String token) {
		cache.put(token, Boolean.TRUE);
	}

	public boolean contains(String token) {
		return cache.getIfPresent(token) != null;
	}
}
