package com.laroka.backend.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.laroka.backend.auth.exception.InvalidCredentialsException;
import com.laroka.backend.shared.security.JwtService;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final StaffUserRepository staffUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public String login(String email, String password) {
		var user = staffUserRepository.findByEmail(email)
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		return jwtService.generateToken(user);
	}
}
