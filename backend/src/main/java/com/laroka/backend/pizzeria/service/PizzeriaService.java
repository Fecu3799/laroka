package com.laroka.backend.pizzeria.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PizzeriaService {

	private final PizzeriaRepository repository;

	public Pizzeria findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new PizzeriaNotFoundException(id));
	}

	public List<Pizzeria> findAll() {
		return repository.findAll();
	}

	public Pizzeria create(Pizzeria pizzeria) {
		return repository.save(pizzeria);
	}

	public Pizzeria update(Integer id, Pizzeria updates) {
		Pizzeria pizzeria = findById(id);
		pizzeria.setName(updates.getName());
		return repository.save(pizzeria);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}
}
