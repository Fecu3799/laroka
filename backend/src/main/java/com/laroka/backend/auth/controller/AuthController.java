package com.laroka.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.auth.dto.LoginRequestDTO;
import com.laroka.backend.auth.dto.LoginResponseDTO;
import com.laroka.backend.auth.service.AuthService;
import com.laroka.backend.shared.security.TokenBlacklist;

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
	@Operation(summary = "Login", description = "Authenticates a staff user and returns JWT token")
	public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
		String token = authService.login(dto.getEmail(), dto.getPassword());
		return ResponseEntity.ok(LoginResponseDTO.builder().token(token).build());
	}

	@PostMapping("/logout")
	@Operation(summary = "Logout", description = "Revokes the current JWT token immediately. Requires authentication.")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			tokenBlacklist.add(header.substring(7));
		}
		return ResponseEntity.ok().build();
	}
}

