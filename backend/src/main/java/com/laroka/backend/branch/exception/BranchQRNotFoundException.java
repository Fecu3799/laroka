package com.laroka.backend.branch.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class BranchQRNotFoundException extends EntityNotFoundException {
	public BranchQRNotFoundException(Integer branchId) {
		super("BranchQR not found for branch id: " + branchId);
	}
}
