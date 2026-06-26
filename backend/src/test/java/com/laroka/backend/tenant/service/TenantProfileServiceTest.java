package com.laroka.backend.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.tenant.entity.Tenant;
import com.laroka.backend.tenant.entity.TenantProfile;
import com.laroka.backend.tenant.exception.TenantProfileNotFoundException;
import com.laroka.backend.tenant.repository.TenantProfileRepository;
import com.laroka.backend.tenant.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantProfileServiceTest {

	@Mock
	private TenantProfileRepository repository;

	@Mock
	private TenantRepository tenantRepository;

	@InjectMocks
	private TenantProfileService service;

	private TenantProfile requestData() {
		return TenantProfile.builder()
			.businessName("LaRoka")
			.description("La mejor pizza de la Patagonia")
			.instagramUrl("https://instagram.com/laroka")
			.whatsapp("+542804123456")
			.build();
	}

	@Test
	void findByTenantId_notFound_throwsTenantProfileNotFound() {
		when(repository.findByTenantId(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findByTenantId(99))
			.isInstanceOf(TenantProfileNotFoundException.class);
	}

	@Test
	void upsert_noExistingProfile_createsNewWithTenant() {
		Tenant tenant = Tenant.builder().id(1).name("LaRoka").build();
		when(repository.findByTenantId(1)).thenReturn(Optional.empty());
		when(tenantRepository.findById(1)).thenReturn(Optional.of(tenant));
		when(repository.save(any(TenantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

		TenantProfile result = service.upsert(1, requestData());

		ArgumentCaptor<TenantProfile> captor = ArgumentCaptor.forClass(TenantProfile.class);
		verify(repository).save(captor.capture());
		TenantProfile saved = captor.getValue();
		assertThat(saved.getTenant()).isSameAs(tenant);
		assertThat(saved.getBusinessName()).isEqualTo("LaRoka");
		assertThat(saved.getDescription()).isEqualTo("La mejor pizza de la Patagonia");
		assertThat(result).isSameAs(saved);
	}

	@Test
	void upsert_existingProfile_updatesFieldsWithoutReassigningTenant() {
		Tenant tenant = Tenant.builder().id(1).name("LaRoka").build();
		TenantProfile existing = TenantProfile.builder()
			.id(10).tenant(tenant)
			.businessName("Viejo nombre").description("Vieja desc")
			.build();
		when(repository.findByTenantId(1)).thenReturn(Optional.of(existing));
		when(repository.save(any(TenantProfile.class))).thenAnswer(inv -> inv.getArgument(0));

		TenantProfile result = service.upsert(1, requestData());

		assertThat(result.getId()).isEqualTo(10);
		assertThat(result.getTenant()).isSameAs(tenant);
		assertThat(result.getBusinessName()).isEqualTo("LaRoka");
		assertThat(result.getDescription()).isEqualTo("La mejor pizza de la Patagonia");
		assertThat(result.getInstagramUrl()).isEqualTo("https://instagram.com/laroka");
		// No debe buscar/reasignar el tenant en una actualización.
		verify(tenantRepository, never()).findById(any());
	}
}
