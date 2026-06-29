package com.laroka.backend.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder vive en su propia config, separado de SecurityConfig, para evitar un
 * ciclo de dependencias de beans: SecurityConfig inyecta JwtAuthenticationFilter por
 * constructor; el filtro depende de StaffUserService (para el chequeo cacheado de
 * usuario activo); y StaffUserService depende de PasswordEncoder. Si el bean siguiera
 * declarado en SecurityConfig, el ciclo se cerraría
 * (SecurityConfig → JwtAuthenticationFilter → StaffUserService → SecurityConfig).
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
