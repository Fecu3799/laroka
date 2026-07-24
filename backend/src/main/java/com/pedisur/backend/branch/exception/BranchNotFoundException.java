package com.pedisur.backend.branch.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class BranchNotFoundException extends EntityNotFoundException {
	public BranchNotFoundException(Integer id) {
		super("Branch not found with id: " + id);
	}

	public BranchNotFoundException(String message) {
		super(message);
	}
}