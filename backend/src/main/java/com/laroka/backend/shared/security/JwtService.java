package com.laroka.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.laroka.backend.staffuser.entity.StaffUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Service
public class JwtService {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration}")
	private long expirationMs;

	public String generateToken(StaffUser user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);
		return Jwts.builder()
			.subject(String.valueOf(user.getId()))
			.claim("role", user.getRole().name())
			.claim("branchId", user.getBranch().getId())
			.issuedAt(now)
			.expiration(expiry)
			.signWith(secretKey())
			.compact();
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (ExpiredJwtException | MalformedJwtException | SignatureException
				| UnsupportedJwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public Integer extractUserId(String token) {
		return Integer.parseInt(parseClaims(token).getSubject());
	}

	public String extractRole(String token) {
		return parseClaims(token).get("role", String.class);
	}

	public Integer extractBranchId(String token) {
		return parseClaims(token).get("branchId", Integer.class);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(secretKey())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private SecretKey secretKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
