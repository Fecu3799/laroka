package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class ProductNotFoundException extends EntityNotFoundException {
	public ProductNotFoundException(Integer id) {
		super("Product not found with id: " + id);
	}

	public ProductNotFoundException(String message) {
		super(message);
	}
}
