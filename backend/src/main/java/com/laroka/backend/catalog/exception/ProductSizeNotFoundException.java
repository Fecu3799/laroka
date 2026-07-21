package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class ProductSizeNotFoundException extends EntityNotFoundException {
	public ProductSizeNotFoundException(Integer id) {
		super("Product size not found with id: " + id);
	}
}
