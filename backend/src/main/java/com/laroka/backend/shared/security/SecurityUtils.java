package com.laroka.backend.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
}
