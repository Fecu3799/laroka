package com.laroka.backend.tenant.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class TenantProfileNotFoundException extends EntityNotFoundException {
	public TenantProfileNotFoundException(Integer tenantId) {
		super("Tenant profile not found for tenant id: " + tenantId);
	}
}
