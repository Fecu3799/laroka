package com.laroka.backend.pizzeria.exception;

import com.laroka.backend.shared.exception.EntityNotFoundException;

public class PizzeriaNotFoundException extends EntityNotFoundException {
	public PizzeriaNotFoundException(Integer id) {
		super("Pizzeria not found with id: " + id);
	}

	public PizzeriaNotFoundException(String message) {
		super(message);
	}
}
