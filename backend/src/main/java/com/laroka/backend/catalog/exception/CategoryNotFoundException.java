package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class CategoryNotFoundException extends EntityNotFoundException {
	public CategoryNotFoundException(Integer id) {
		super("Category not found with id: " + id);
	}

	public CategoryNotFoundException(String message) {
		super(message);
	}
}
