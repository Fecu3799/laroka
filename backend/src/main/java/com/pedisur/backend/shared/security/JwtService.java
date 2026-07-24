package com.pedisur.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.entity.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Service
public class JwtService {

	@Value("${jwt.secret}")
	private String secret;

	// US-SEC-05: secret secundario opcional para grace period durante la rotación
	// de JWT_SECRET. Vacío por defecto. Si no está configurado, no se intenta el
	// fallback y solo se valida contra el secret primario.
	@Value("${jwt.secret-previous:}")
	private String secretPrevious;

	@Value("${jwt.expiration}")
	private long expirationMs;

	public String generateToken(StaffUser user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		JwtBuilder builder = Jwts.builder()
			.subject(String.valueOf(user.getId()))
			.claim("name", user.getName())
			.claim("role", user.getRole().name())
			.claim("tenantId", user.getBranch().getTenant().getId())
			.claim("tenantName", user.getBranch().getTenant().getName())
			.issuedAt(now)
			.expiration(expiry)
			.signWith(secretKey(secret));

		if (user.getRole() != UserRole.ADMIN) {
			builder.claim("branchId", user.getBranch().getId());
			builder.claim("branchName", user.getBranch().getName());
		}

		return builder.compact();
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

	public Integer extractTenantId(String token) {
		return parseClaims(token).get("tenantId", Integer.class);
	}

	// US-SEC-05: verifica primero contra el secret primario. Si falla y hay un
	// secret secundario configurado (JWT_SECRET_PREVIOUS), reintenta con él. Esto
	// permite rotar JWT_SECRET sin invalidar sesiones activas abruptamente: durante
	// el grace period los tokens firmados con el secret anterior siguen siendo
	// válidos. Si ambos fallan (o no hay secundario), el token es inválido.
	private Claims parseClaims(String token) {
		try {
			return parseClaimsWith(token, secret);
		} catch (JwtException | IllegalArgumentException primaryFailure) {
			if (secretPrevious != null && !secretPrevious.isBlank()) {
				return parseClaimsWith(token, secretPrevious);
			}
			throw primaryFailure;
		}
	}

	private Claims parseClaimsWith(String token, String signingSecret) {
		return Jwts.parser()
			.verifyWith(secretKey(signingSecret))
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private SecretKey secretKey(String signingSecret) {
		return Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
	}
}
