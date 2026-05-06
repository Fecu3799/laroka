package com.laroka.backend.branch.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository repository;
	private final PizzeriaRepository pizzeriaRepository;

	public Branch findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new BranchNotFoundException(id));
	}

	public List<Branch> findByPizzeria(Integer pizzeriaId) {
		validatePizzeriaExists(pizzeriaId);
		return repository.findByPizzeriaId(pizzeriaId);
	}

	public List<Branch> findAll() {
		return repository.findAll();
	}

	public Branch create(Branch branch) {
		Pizzeria pizzeria = validatePizzeriaExists(branch.getPizzeria().getId());
		branch.setPizzeria(pizzeria);
		return repository.save(branch);
	}

	public Branch update(Integer id, Branch updates) {
		Branch branch = findById(id);
		Pizzeria pizzeria = validatePizzeriaExists(updates.getPizzeria().getId());
		branch.setName(updates.getName());
		branch.setAddress(updates.getAddress());
		branch.setEstimatedDeliveryMinutes(updates.getEstimatedDeliveryMinutes());
		branch.setPhone(updates.getPhone());
		branch.setPizzeria(pizzeria);
		return repository.save(branch);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	private Pizzeria validatePizzeriaExists(Integer pizzeriaId) {
		return pizzeriaRepository.findById(pizzeriaId)
			.orElseThrow(() -> new PizzeriaNotFoundException(pizzeriaId));
	}
}
