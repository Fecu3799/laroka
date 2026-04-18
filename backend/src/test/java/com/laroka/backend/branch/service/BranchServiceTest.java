package com.laroka.backend.branch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.pizzeria.entity.Pizzeria;
import com.laroka.backend.pizzeria.exception.PizzeriaNotFoundException;
import com.laroka.backend.pizzeria.repository.PizzeriaRepository;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

	@Mock
	private BranchRepository branchRepository;

	@Mock
	private PizzeriaRepository pizzeriaRepository;

	@InjectMocks
	private BranchService service;

	private Pizzeria pizzeria() {
		return Pizzeria.builder().id(1).name("LaRoka").build();
	}

	private Branch branch(Pizzeria pizzeria) {
		return Branch.builder().id(1).name("Playa Unión").address("Av. Principal 123").pizzeria(pizzeria).build();
	}

	@Test
	void findById_returnsExistingBranch() {
		Branch branch = branch(pizzeria());
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

		Branch result = service.findById(1);

		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getName()).isEqualTo("Playa Unión");
	}

	@Test
	void findById_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findById(99))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void findAll_returnsAllBranches() {
		Pizzeria p = pizzeria();
		when(branchRepository.findAll()).thenReturn(List.of(branch(p), branch(p)));

		List<Branch> result = service.findAll();

		assertThat(result).hasSize(2);
	}

	@Test
	void findByPizzeria_validPizzeria_returnsBranches() {
		Pizzeria p = pizzeria();
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.findByPizzeriaId(1)).thenReturn(List.of(branch(p)));

		List<Branch> result = service.findByPizzeria(1);

		assertThat(result).hasSize(1);
	}

	@Test
	void findByPizzeria_invalidPizzeria_throwsPizzeriaNotFoundException() {
		when(pizzeriaRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByPizzeria(99))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void create_validBranch_savesAndReturns() {
		Pizzeria p = pizzeria();
		Branch branch = branch(p);
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.save(any(Branch.class))).thenReturn(branch);

		Branch result = service.create(branch);

		assertThat(result.getName()).isEqualTo("Playa Unión");
		verify(branchRepository).save(branch);
	}

	@Test
	void create_invalidPizzeria_throwsPizzeriaNotFoundException() {
		Branch branch = Branch.builder().id(1).name("Test").address("Addr").pizzeria(Pizzeria.builder().id(99).build()).build();
		when(pizzeriaRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(branch))
			.isInstanceOf(PizzeriaNotFoundException.class);
	}

	@Test
	void update_existingBranch_updatesAndReturns() {
		Pizzeria p = pizzeria();
		Branch existing = branch(p);
		Branch updates = Branch.builder().name("Rawson").address("Nueva 456").pizzeria(p).build();
		when(branchRepository.findById(1)).thenReturn(Optional.of(existing));
		when(pizzeriaRepository.findById(1)).thenReturn(Optional.of(p));
		when(branchRepository.save(any(Branch.class))).thenReturn(existing);

		Branch result = service.update(1, updates);

		assertThat(result.getName()).isEqualTo("Rawson");
	}

	@Test
	void update_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99, branch(pizzeria())))
			.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void delete_existingBranch_deletes() {
		Branch branch = branch(pizzeria());
		when(branchRepository.findById(1)).thenReturn(Optional.of(branch));

		service.delete(1);

		verify(branchRepository).delete(branch);
	}

	@Test
	void delete_notFound_throwsBranchNotFoundException() {
		when(branchRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99))
			.isInstanceOf(BranchNotFoundException.class);
	}
}
