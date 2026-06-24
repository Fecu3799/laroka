package com.laroka.backend.staffuser.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class StaffUserNotFoundException extends EntityNotFoundException {
	public StaffUserNotFoundException(Integer id) {
		super("Staff user not found with id: " + id);
	}
}
