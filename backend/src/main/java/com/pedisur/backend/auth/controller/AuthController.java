package com.pedisur.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedisur.backend.auth.dto.LoginRequestDTO;
import com.pedisur.backend.auth.dto.LoginResponseDTO;
import com.pedisur.backend.auth.dto.RefreshRequestDTO;
import com.pedisur.backend.auth.service.AuthService;
import com.pedisur.backend.auth.service.LoginTokens;
import com.pedisur.backend.shared.security.CustomUserDetails;
import com.pedisur.backend.shared.security.TokenBlacklist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

	private final AuthService authService;
	private final TokenBlacklist tokenBlacklist;

	@PostMapping("/login")
	@Operation(summary = "Login", description = "Authenticates a staff user and returns access + refresh tokens")
	public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
		LoginTokens tokens = authService.login(dto.getEmail(), dto.getPassword());
		return ResponseEntity.ok(LoginResponseDTO.builder()
			.token(tokens.accessToken())
			.refreshToken(tokens.refreshToken())
			.build());
	}

	@PostMapping("/refresh")
	@Operation(summary = "Refresh token", description = "Issues a new access token using a valid refresh token (rotates the refresh token)")
	public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO dto) {
		LoginTokens tokens = authService.refresh(dto.getRefreshToken());
		return ResponseEntity.ok(LoginResponseDTO.builder()
			.token(tokens.accessToken())
			.refreshToken(tokens.refreshToken())
			.build());
	}

	@PostMapping("/logout")
	@Operation(summary = "Logout", description = "Revokes the current JWT token immediately and invalidates all refresh tokens for the user")
	public ResponseEntity<Void> logout(HttpServletRequest request, Authentication authentication) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			tokenBlacklist.add(header.substring(7));
		}
		if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
			authService.revokeAllRefreshTokens(principal.getUserId());
		}
		return ResponseEntity.ok().build();
	}
}
