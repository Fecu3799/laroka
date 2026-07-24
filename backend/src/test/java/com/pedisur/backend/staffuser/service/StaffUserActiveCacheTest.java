package com.pedisur.backend.staffuser.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.repository.BranchRepository;
import com.pedisur.backend.shared.config.CacheConfig;
import com.pedisur.backend.staffuser.entity.StaffUser;
import com.pedisur.backend.staffuser.repository.StaffUserRepository;
import com.pedisur.backend.tenant.entity.Tenant;

/**
 * Verifica que el cache "staffUserActive" efectivamente funciona ahora que @Cacheable
 * vive en el service (StaffUserService.isActive) y no en el repositorio. Levanta un
 * contexto mínimo con CacheConfig real + StaffUserService real + repositorio mockeado
 * (sin DB), de modo que el advice de cache sí se teje sobre el proxy del bean.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, StaffUserActiveCacheTest.TestConfig.class})
class StaffUserActiveCacheTest {

	@Configuration
	static class TestConfig {
		@Bean
		StaffUserRepository staffUserRepository() {
			return mock(StaffUserRepository.class);
		}

		@Bean
		BranchRepository branchRepository() {
			return mock(BranchRepository.class);
		}

		@Bean
		PasswordEncoder passwordEncoder() {
			return mock(PasswordEncoder.class);
		}

		@Bean
		StaffUserService staffUserService(StaffUserRepository staffUserRepository,
				BranchRepository branchRepository, PasswordEncoder passwordEncoder) {
			return new StaffUserService(staffUserRepository, branchRepository, passwordEncoder);
		}
	}

	@Autowired
	private StaffUserService staffUserService;

	@Autowired
	private StaffUserRepository staffUserRepository;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void resetState() {
		// El repo y el cache son singletons del contexto: aislamos cada test.
		cacheManager.getCache("staffUserActive").clear();
		reset(staffUserRepository);
	}

	@Test
	void isActive_secondCall_servedFromCacheWithoutHittingRepository() {
		when(staffUserRepository.findActiveById(1)).thenReturn(Optional.of(true));

		staffUserService.isActive(1);
		staffUserService.isActive(1);
		staffUserService.isActive(1);

		// Una sola consulta a la DB pese a tres lecturas: el cache funciona.
		verify(staffUserRepository, times(1)).findActiveById(1);
	}

	@Test
	void setStatus_evictsCache_nextIsActiveHitsRepositoryAgain() {
		when(staffUserRepository.findActiveById(1)).thenReturn(Optional.of(true));

		// Pobla el cache.
		staffUserService.isActive(1);

		// setStatus está anotado con @CacheEvict(value="staffUserActive", key="#id").
		Tenant tenant = Tenant.builder().id(7).build();
		Branch branch = Branch.builder().id(1).tenant(tenant).build();
		StaffUser user = StaffUser.builder().id(1).branch(branch).active(true).build();
		when(staffUserRepository.findByIdWithBranchAndTenant(1)).thenReturn(Optional.of(user));
		when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

		staffUserService.setStatus(1, 7, 99, false);

		// Tras la evicción, la próxima lectura vuelve a la DB.
		staffUserService.isActive(1);

		verify(staffUserRepository, times(2)).findActiveById(1);
	}
}
