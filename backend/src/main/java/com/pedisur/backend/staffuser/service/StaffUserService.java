package com.pedisur.backend.staffuser.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.exception.BranchNotFoundException;
import com.pedisur.backend.branch.repository.BranchRepository;
import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.exception.StaffUserNotFoundException;
import com.pedisur.backend.staffuser.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffUserService {

	private final StaffUserRepository staffUserRepository;
	private final BranchRepository branchRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * Estado activo del usuario, cacheado para no pegar a la DB en cada request
	 * (JwtAuthenticationFilter lo consulta por petición). El cache vive en la capa
	 * de service —no en el repositorio— para que el advice de @Cacheable se aplique
	 * de forma confiable sobre el proxy del bean; la invalidación está en setStatus
	 * vía @CacheEvict sobre el mismo cache "staffUserActive".
	 */
	@Cacheable(value = "staffUserActive", key = "#id")
	public Optional<Boolean> isActive(Integer id) {
		return staffUserRepository.findActiveById(id);
	}

	public StaffUser create(StaffUser staffUser) {
		Branch branch = branchRepository.findById(staffUser.getBranch().getId())
			.orElseThrow(() -> new BranchNotFoundException(staffUser.getBranch().getId()));

		String domain = branch.getTenant().getEmailDomain();
		staffUser.setEmail(generateEmail(staffUser.getName(), domain));
		staffUser.setBranch(branch);
		staffUser.setPasswordHash(passwordEncoder.encode(staffUser.getPasswordHash()));

		return staffUserRepository.save(staffUser);
	}

	public StaffUser update(Integer id, Integer tenantId, StaffUser patch) {
		StaffUser existing = staffUserRepository.findByIdWithBranchAndTenant(id)
			.orElseThrow(() -> new StaffUserNotFoundException(id));

		if (!existing.getBranch().getTenant().getId().equals(tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff user does not belong to your tenant");
		}

		Integer newBranchId = patch.getBranch().getId();
		if (!branchRepository.existsByIdAndTenantId(newBranchId, tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch does not belong to your tenant");
		}

		if (!existing.getName().equals(patch.getName())) {
			String domain = existing.getBranch().getTenant().getEmailDomain();
			existing.setEmail(generateEmailExcluding(patch.getName(), id, domain));
			existing.setName(patch.getName());
		}

		existing.setRole(patch.getRole());
		existing.setBranch(branchRepository.findById(newBranchId)
			.orElseThrow(() -> new BranchNotFoundException(newBranchId)));

		return staffUserRepository.save(existing);
	}

	@CacheEvict(value = "staffUserActive", key = "#id")
	public void setStatus(Integer id, Integer tenantId, Integer authenticatedUserId, boolean active) {
		StaffUser existing = staffUserRepository.findByIdWithBranchAndTenant(id)
			.orElseThrow(() -> new StaffUserNotFoundException(id));

		if (!existing.getBranch().getTenant().getId().equals(tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff user does not belong to your tenant");
		}

		if (!active && id.equals(authenticatedUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ADMIN no puede desactivarse a sí mismo");
		}

		existing.setActive(active);
		staffUserRepository.save(existing);
	}

	public void resetPassword(Integer id, Integer tenantId, String newPassword) {
		StaffUser existing = staffUserRepository.findByIdWithBranchAndTenant(id)
			.orElseThrow(() -> new StaffUserNotFoundException(id));

		if (!existing.getBranch().getTenant().getId().equals(tenantId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff user does not belong to your tenant");
		}

		existing.setPasswordHash(passwordEncoder.encode(newPassword));
		staffUserRepository.save(existing);
	}

	public List<StaffUser> findAllByTenantId(Integer tenantId) {
		return staffUserRepository.findAllByTenantId(tenantId);
	}

	String generateEmail(String name, String domain) {
		String normalized = normalizeName(name);
		String baseEmail = normalized + "@" + domain;
		if (!staffUserRepository.existsByEmail(baseEmail)) {
			return baseEmail;
		}
		int suffix = 2;
		while (true) {
			String candidate = normalized + suffix + "@" + domain;
			if (!staffUserRepository.existsByEmail(candidate)) {
				return candidate;
			}
			suffix++;
		}
	}

	String generateEmailExcluding(String name, Integer excludeId, String domain) {
		String normalized = normalizeName(name);
		String baseEmail = normalized + "@" + domain;
		if (!staffUserRepository.existsByEmailAndIdNot(baseEmail, excludeId)) {
			return baseEmail;
		}
		int suffix = 2;
		while (true) {
			String candidate = normalized + suffix + "@" + domain;
			if (!staffUserRepository.existsByEmailAndIdNot(candidate, excludeId)) {
				return candidate;
			}
			suffix++;
		}
	}

	private String normalizeName(String name) {
		return Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
			.toLowerCase()
			.replaceAll("\\s+", ".")
			.replaceAll("[^a-z.]", "")
			.replaceAll("^\\.+|\\.+$", "");
	}
}
