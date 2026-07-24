package com.pedisur.backend.catalog.exception;

import com.pedisur.backend.shared.exception.EntityNotFoundException;

public class CategoryTypeNotFoundException extends EntityNotFoundException {
	public CategoryTypeNotFoundException(Integer id) {
		super("Category type not found with id: " + id);
	}
}
