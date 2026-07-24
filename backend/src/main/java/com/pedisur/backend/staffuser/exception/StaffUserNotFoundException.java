package com.pedisur.backend.staffuser.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class StaffUserNotFoundException extends EntityNotFoundException {
	public StaffUserNotFoundException(Integer id) {
		super("Staff user not found with id: " + id);
	}
}
