package com.laroka.backend.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.laroka.backend.branch.repository.BranchRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

	private final BranchRepository branchRepository;

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
			Integer branchId;
			try {
				branchId = Integer.parseInt(headerVal.trim());
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"X-Branch-Id header must be a valid integer");
			}
			if (!branchRepository.existsByIdAndTenantId(branchId, principal.getTenantId())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Branch does not belong to your tenant");
			}
			return branchId;
		}

		return principal.getBranchId();
	}

	/**
	 * Valida que el usuario tenga alcance sobre la sucursal indicada por path (no por
	 * header X-Branch-Id). MANAGER/STAFF: solo su propia sucursal (branchId del token) —
	 * 403 si {@code branchId} no coincide. ADMIN: cualquier sucursal de su tenant — 403 si
	 * la sucursal no pertenece a su tenant. Evita que un MANAGER de la sucursal A opere
	 * sobre la sucursal B pasando su id en el path.
	 */
	public void validateBranchScope(CustomUserDetails principal, Integer branchId) {
		boolean isAdmin = principal.getAuthorities().stream()
			.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

		if (isAdmin) {
			if (!branchRepository.existsByIdAndTenantId(branchId, principal.getTenantId())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Branch does not belong to your tenant");
			}
			return;
		}

		if (!branchId.equals(principal.getBranchId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
				"Branch is outside your scope");
		}
	}
}
