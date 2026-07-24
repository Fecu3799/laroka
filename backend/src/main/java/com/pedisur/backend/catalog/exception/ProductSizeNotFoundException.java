package com.pedisur.backend.catalog.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class ProductSizeNotFoundException extends EntityNotFoundException {
	public ProductSizeNotFoundException(Integer id) {
		super("Product size not found with id: " + id);
	}
}
