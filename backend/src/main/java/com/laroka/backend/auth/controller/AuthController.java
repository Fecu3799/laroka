package com.laroka.backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laroka.backend.auth.dto.LoginRequestDTO;
import com.laroka.backend.auth.dto.LoginResponseDTO;
import com.laroka.backend.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login")
	@Operation(summary = "Login", description = "Authenticates a staff user and returns JWT token")
	public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
		String token = authService.login(dto.getEmail(), dto.getPassword());
		return ResponseEntity.ok(LoginResponseDTO.builder().token(token).build());
	}
}
