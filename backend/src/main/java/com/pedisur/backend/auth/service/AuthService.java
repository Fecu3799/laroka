package com.pedisur.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pedisur.backend.auth.entity.RefreshToken;
import com.pedisur.backend.auth.exception.InvalidCredentialsException;
import com.pedisur.backend.auth.exception.RefreshTokenInvalidException;
import com.pedisur.backend.auth.repository.RefreshTokenRepository;
import com.pedisur.backend.shared.security.JwtService;
import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

	private final StaffUserRepository staffUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenRepository refreshTokenRepository;

	@Value("${refresh.token.expiration-days:7}")
	private long refreshExpirationDays;

	public LoginTokens login(String email, String password) {
		var user = staffUserRepository.findByEmailWithBranchAndTenant(email)
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		if (!user.isActive()) {
			throw new InvalidCredentialsException("Usuario desactivado");
		}

		String accessToken = jwtService.generateToken(user);
		String rawRefreshToken = generateAndPersistRefreshToken(user);
		return new LoginTokens(accessToken, rawRefreshToken);
	}

	public LoginTokens refresh(String rawRefreshToken) {
		String hash = sha256(rawRefreshToken);
		RefreshToken rt = refreshTokenRepository.findByTokenHash(hash)
			.orElseThrow(RefreshTokenInvalidException::new);

		if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
			throw new RefreshTokenInvalidException();
		}

		rt.setRevoked(true);
		refreshTokenRepository.save(rt);

		StaffUser user = staffUserRepository.findByIdWithBranchAndTenant(rt.getStaffUser().getId())
			.orElseThrow(RefreshTokenInvalidException::new);

		String newAccessToken = jwtService.generateToken(user);
		String newRawRefreshToken = generateAndPersistRefreshToken(user);
		return new LoginTokens(newAccessToken, newRawRefreshToken);
	}

	public void revokeAllRefreshTokens(Integer staffUserId) {
		refreshTokenRepository.revokeAllByStaffUserId(staffUserId);
	}

	private String generateAndPersistRefreshToken(StaffUser user) {
		String rawToken = UUID.randomUUID().toString();
		RefreshToken rt = RefreshToken.builder()
			.tokenHash(sha256(rawToken))
			.staffUser(user)
			.expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
			.revoked(false)
			.createdAt(Instant.now())
			.build();
		refreshTokenRepository.save(rt);
		return rawToken;
	}

	static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(64);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
