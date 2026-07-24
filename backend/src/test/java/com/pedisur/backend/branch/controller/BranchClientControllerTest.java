package com.pedisur.backend.branch.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pedisur.backend.branch.dto.BranchPublicDTO;
import com.pedisur.backend.branch.entity.Branch;
import com.pedisur.backend.branch.mapper.BranchMapper;
import com.pedisur.backend.branch.service.BranchService;
import com.pedisur.backend.catalog.mapper.MenuMapper;
import com.pedisur.backend.catalog.service.ProductService;
import com.pedisur.backend.shared.security.JwtService;
import com.pedisur.backend.shared.security.TokenBlacklist;
import com.pedisur.backend.staffuser.service.StaffUserService;
import com.pedisur.backend.tenant.entity.Tenant;

@WebMvcTest(controllers = BranchClientController.class)
@AutoConfigureMockMvc(addFilters = false)
class BranchClientControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BranchService branchService;

	@MockitoBean
	private BranchMapper branchMapper;

	@MockitoBean
	private ProductService productService;

	@MockitoBean
	private MenuMapper menuMapper;

	// Beans requeridos para construir el JwtAuthenticationFilter que @WebMvcTest
	// detecta como Filter, aunque la seguridad esté deshabilitada (addFilters = false).
	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private TokenBlacklist tokenBlacklist;

	@MockitoBean
	private StaffUserService staffUserService;

	private Branch branch(Integer tenantId) {
		return Branch.builder()
			.id(1).name("Playa Unión").address("Av. Principal 123")
			.estimatedDeliveryMinutes(30).phone("+542804123456")
			.tenant(Tenant.builder().id(tenantId).build())
			.build();
	}

	@Test
	void findAll_withoutTenantId_returns400() throws Exception {
		mockMvc.perform(get("/branches"))
			.andExpect(status().isBadRequest());

		verify(branchService, never()).findActiveByTenant(any());
	}

	@Test
	void findAll_withTenantId_returnsOnlyBranchesOfThatTenant() throws Exception {
		when(branchService.findActiveByTenant(eq(1))).thenReturn(List.of(branch(1)));
		when(branchMapper.toPublicDTO(any(Branch.class)))
			.thenReturn(BranchPublicDTO.builder().id(1).name("Playa Unión").build());

		mockMvc.perform(get("/branches").param("tenantId", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Playa Unión"));

		verify(branchService).findActiveByTenant(1);
	}
}
