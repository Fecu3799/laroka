package com.laroka.backend.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.HstsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Value("${cors.allowed-origins}")
	private String corsAllowedOrigins;

	private final JwtAuthenticationFilter jwtFilter;

	public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
		this.jwtFilter = jwtFilter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			// US-SEC-01: headers de seguridad HTTP en todas las respuestas.
			.headers(headers -> headers
				// X-Frame-Options: DENY — anti-clickjacking (default de Spring, explícito).
				.frameOptions(frame -> frame.deny())
				// X-Content-Type-Options: nosniff (default de Spring, explícito).
				.contentTypeOptions(Customizer.withDefaults())
				// Referrer-Policy: strict-origin-when-cross-origin.
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				// Permissions-Policy: deshabilita geolocation, camera y microphone.
				.permissionsPolicyHeader(permissions -> permissions.policy("geolocation=(), camera=(), microphone=()"))
				// HSTS: el writer por defecto solo emite sobre HTTPS y con formato
				// "max-age=... ; includeSubDomains" (espacios alrededor del ';'). En
				// producción la app corre detrás del proxy TLS de Render (ve HTTP), así
				// que lo desactivamos y emitimos el valor exacto en toda respuesta.
				.httpStrictTransportSecurity(HstsConfig::disable)
				.addHeaderWriter(new StaticHeadersWriter(
					"Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
			)
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
				.requestMatchers(HttpMethod.GET, "/branches/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/tenants/*/profile").permitAll()
				.requestMatchers(HttpMethod.POST, "/orders").permitAll()
				.requestMatchers(HttpMethod.GET, "/orders/*/status").permitAll()
				.requestMatchers(HttpMethod.GET, "/orders/*/items").permitAll()
				.requestMatchers(HttpMethod.POST, "/orders/*/cancel").permitAll()
				.requestMatchers(HttpMethod.POST, "/payments/initiate").permitAll()
				.requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
				.requestMatchers(HttpMethod.POST, "/push/subscribe").permitAll()
				.requestMatchers(HttpMethod.DELETE, "/push/subscribe").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
				.requestMatchers(HttpMethod.GET, "/swagger-ui.html").permitAll()
				.requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
				.requestMatchers("/backoffice/shifts/**").hasAnyRole("MANAGER", "ADMIN")
				.requestMatchers("/backoffice/**").authenticated()
				.anyRequest().authenticated()
			)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(HttpStatus.UNAUTHORIZED.value());
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					response.getWriter().write("{\"status\":401,\"message\":\"Unauthorized\"}");
				})
				.accessDeniedHandler((request, response, accessDeniedException) -> {
					response.setStatus(HttpStatus.FORBIDDEN.value());
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					response.getWriter().write("{\"status\":403,\"message\":\"Access denied\"}");
				})
			)
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key", "X-Branch-Id"));
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
