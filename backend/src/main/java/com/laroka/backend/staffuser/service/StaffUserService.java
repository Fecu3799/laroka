package com.laroka.backend.staffuser.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.shared.exception.BusinessException;
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
		if (staffUserRepository.findByEmail(staffUser.getEmail()).isPresent()) {
			throw new BusinessException("Email already in use");
		}

		Branch branch = branchRepository.findById(staffUser.getBranch().getId())
			.orElseThrow(() -> new BranchNotFoundException(staffUser.getBranch().getId()));

		staffUser.setBranch(branch);
		String hashedPassword = passwordEncoder.encode(staffUser.getPasswordHash());
		staffUser.setPasswordHash(hashedPassword);

		return staffUserRepository.save(staffUser);
	}
}
