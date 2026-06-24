package com.laroka.backend.staffuser.service;

import java.text.Normalizer;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffUserService {

	private final StaffUserRepository staffUserRepository;
	private final BranchRepository branchRepository;
	private final PasswordEncoder passwordEncoder;

	public StaffUser create(StaffUser staffUser) {
		String generatedEmail = generateEmail(staffUser.getName());
		staffUser.setEmail(generatedEmail);

		Branch branch = branchRepository.findById(staffUser.getBranch().getId())
			.orElseThrow(() -> new BranchNotFoundException(staffUser.getBranch().getId()));

		staffUser.setBranch(branch);
		String hashedPassword = passwordEncoder.encode(staffUser.getPasswordHash());
		staffUser.setPasswordHash(hashedPassword);

		return staffUserRepository.save(staffUser);
	}

	public List<StaffUser> findAllByTenantId(Integer tenantId) {
		return staffUserRepository.findAllByTenantId(tenantId);
	}

	String generateEmail(String name) {
		String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
			.toLowerCase()
			.replaceAll("\\s+", ".")
			.replaceAll("[^a-z.]", "")
			.replaceAll("^\\.+|\\.+$", "");

		String baseEmail = normalized + "@laroka.com";
		if (!staffUserRepository.existsByEmail(baseEmail)) {
			return baseEmail;
		}

		int suffix = 2;
		while (true) {
			String candidate = normalized + suffix + "@laroka.com";
			if (!staffUserRepository.existsByEmail(candidate)) {
				return candidate;
			}
			suffix++;
		}
	}
}
