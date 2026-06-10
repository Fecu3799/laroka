package com.laroka.backend.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class SecurityUtils {

	public Integer getAuthenticatedUserBranchId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof CustomUserDetails) {
			return ((CustomUserDetails) principal).getBranchId();
		}
		return null;
	}

	public Integer resolveBranchId(CustomUserDetails principal, HttpServletRequest request) {
		boolean isAdmin = principal.getAuthorities().stream()
			.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

		if (isAdmin) {
			String headerVal = request.getHeader("X-Branch-Id");
			if (headerVal == null || headerVal.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"X-Branch-Id header is required for ADMIN role");
			}
			try {
				return Integer.parseInt(headerVal.trim());
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"X-Branch-Id header must be a valid integer");
			}
		}

		return principal.getBranchId();
	}
}
