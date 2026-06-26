package com.laroka.backend.shared.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.laroka.backend.staffuser.repository.StaffUserRepository;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final TokenBlacklist tokenBlacklist;
	private final StaffUserRepository staffUserRepository;

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return true;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}

		String token = header.substring(7);
		if (tokenBlacklist.contains(token)) {
			writeError(response, HttpStatus.UNAUTHORIZED, "Token revoked");
			return;
		}
		try {
			Integer userId = jwtService.extractUserId(token);

			Optional<Boolean> active = staffUserRepository.findActiveById(userId);
			if (active.isPresent() && Boolean.FALSE.equals(active.get())) {
				writeError(response, HttpStatus.UNAUTHORIZED, "Usuario desactivado");
				return;
			}

			String role = jwtService.extractRole(token);
			Integer branchId = jwtService.extractBranchId(token);
			Integer tenantId = jwtService.extractTenantId(token);

			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
			CustomUserDetails principal = new CustomUserDetails(userId, branchId, tenantId, null, null, List.of(authority));
			UsernamePasswordAuthenticationToken auth =
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(auth);
			chain.doFilter(request, response);
		} catch (ExpiredJwtException e) {
			writeError(response, HttpStatus.UNAUTHORIZED, "Session expired");
		} catch (JwtException e) {
			writeError(response, HttpStatus.UNAUTHORIZED, "Invalid token");
		}
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
			"{\"status\":" + status.value() + ",\"message\":\"" + message + "\",\"timestamp\":\"" + Instant.now() + "\"}"
		);
	}
}
