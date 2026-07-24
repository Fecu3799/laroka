package com.pedisur.backend.branch.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class BranchQRNotFoundException extends EntityNotFoundException {
	public BranchQRNotFoundException(Integer branchId) {
		super("BranchQR not found for branch id: " + branchId);
	}
}
