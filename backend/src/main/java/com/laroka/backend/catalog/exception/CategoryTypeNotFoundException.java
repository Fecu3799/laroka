package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class CategoryTypeNotFoundException extends EntityNotFoundException {
	public CategoryTypeNotFoundException(Integer id) {
		super("Category type not found with id: " + id);
	}
}
