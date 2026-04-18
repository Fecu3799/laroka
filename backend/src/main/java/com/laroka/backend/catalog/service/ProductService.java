package com.laroka.backend.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.exception.CategoryNotFoundException;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.repository.CategoryRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository repository;
	private final CategoryRepository categoryRepository;
	private final BranchRepository branchRepository;
	private final PizzeriaRepository pizzeriaRepository;

	public Product findById(Integer id) {
		return repository.findById(id)
			.orElseThrow(() -> new ProductNotFoundException(id));
	}

	public List<Product> findByBranch(Integer branchId) {
		validateBranchExists(branchId);
		return repository.findByBranchId(branchId);
	}

	public List<Product> findAvailableByBranch(Integer branchId) {
		validateBranchExists(branchId);
		return repository.findByBranchIdAndAvailableTrue(branchId);
	}

	public List<Product> findByCategory(Integer categoryId) {
		validateCategoryExists(categoryId);
		return repository.findByCategoryId(categoryId);
	}

	public List<Product> findByPizzeria(Integer pizzeriaId) {
		validatePizzeriaExists(pizzeriaId);
		return repository.findByPizzeriaId(pizzeriaId);
	}

	public List<Product> findAll() {
		return repository.findAll();
	}

	public Product create(Product product) {
		Category category = validateCategoryExists(product.getCategory().getId());
		Branch branch = validateBranchExists(product.getBranch().getId());
		Pizzeria pizzeria = validatePizzeriaExists(product.getPizzeria().getId());
		product.setCategory(category);
		product.setBranch(branch);
		product.setPizzeria(pizzeria);
		return repository.save(product);
	}

	public Product update(Integer id, Product updates) {
		Product product = findById(id);
		Category category = validateCategoryExists(updates.getCategory().getId());
		Branch branch = validateBranchExists(updates.getBranch().getId());
		Pizzeria pizzeria = validatePizzeriaExists(updates.getPizzeria().getId());
		product.setName(updates.getName());
		product.setDescription(updates.getDescription());
		product.setPrice(updates.getPrice());
		product.setImageUrl(updates.getImageUrl());
		product.setAvailable(updates.getAvailable());
		product.setCategory(category);
		product.setBranch(branch);
		product.setPizzeria(pizzeria);
		return repository.save(product);
	}

	public void delete(Integer id) {
		repository.delete(findById(id));
	}

	public Product updateAvailability(Integer id, Boolean available, Integer userBranchId) {
		Product product = findById(id);
		if (userBranchId != null && !product.getBranch().getId().equals(userBranchId)) {
			throw new BusinessException("Product does not belong to user's branch");
		}
		product.setAvailable(available);
		return repository.save(product);
	}

	private Category validateCategoryExists(Integer categoryId) {
		return categoryRepository.findById(categoryId)
			.orElseThrow(() -> new CategoryNotFoundException(categoryId));
	}

	private Branch validateBranchExists(Integer branchId) {
		return branchRepository.findById(branchId)
			.orElseThrow(() -> new BranchNotFoundException(branchId));
	}

	private Pizzeria validatePizzeriaExists(Integer pizzeriaId) {
		return pizzeriaRepository.findById(pizzeriaId)
			.orElseThrow(() -> new PizzeriaNotFoundException(pizzeriaId));
	}
}
