package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class BranchProductNotFoundException extends EntityNotFoundException {
    public BranchProductNotFoundException(Integer branchId, Integer productId) {
        super("Product " + productId + " not found in branch " + branchId);
    }
}
