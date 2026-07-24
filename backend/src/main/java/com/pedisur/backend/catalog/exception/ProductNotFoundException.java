package com.pedisur.backend.catalog.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class ProductNotFoundException extends EntityNotFoundException {
	public ProductNotFoundException(Integer id) {
		super("Product not found with id: " + id);
	}

	public ProductNotFoundException(String message) {
		super(message);
	}
}
