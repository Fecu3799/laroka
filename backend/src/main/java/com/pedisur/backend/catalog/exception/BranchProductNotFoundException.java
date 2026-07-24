package com.pedisur.backend.catalog.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class BranchProductNotFoundException extends EntityNotFoundException {
    public BranchProductNotFoundException(Integer branchId, Integer productId) {
        super("Product " + productId + " not found in branch " + branchId);
    }
}
