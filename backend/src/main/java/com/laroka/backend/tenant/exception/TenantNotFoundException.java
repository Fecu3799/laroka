package com.laroka.backend.tenant.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class TenantNotFoundException extends EntityNotFoundException {
	public TenantNotFoundException(Integer id) {
		super("Tenant not found with id: " + id);
	}

	public TenantNotFoundException(String message) {
		super(message);
	}
}
