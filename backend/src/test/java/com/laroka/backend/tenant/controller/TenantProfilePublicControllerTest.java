package com.laroka.backend.tenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.laroka.backend.shared.security.JwtService;
import com.laroka.backend.shared.security.TokenBlacklist;
import com.laroka.backend.staffuser.service.StaffUserService;
import com.laroka.backend.tenant.dto.TenantProfilePublicDTO;
import com.laroka.backend.tenant.entity.TenantProfile;
import com.laroka.backend.tenant.exception.TenantProfileNotFoundException;
import com.laroka.backend.tenant.mapper.TenantProfileMapper;
import com.laroka.backend.tenant.service.TenantProfileService;

@WebMvcTest(controllers = TenantProfilePublicController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantProfilePublicControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TenantProfileService service;

	@MockitoBean
	private TenantProfileMapper mapper;

	// Beans requeridos para construir el JwtAuthenticationFilter que @WebMvcTest
	// detecta como Filter, aunque la seguridad esté deshabilitada (addFilters = false).
	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private TokenBlacklist tokenBlacklist;

	@MockitoBean
	private StaffUserService staffUserService;

	@Test
	void getProfile_existingProfile_returnsPublicDTO() throws Exception {
		when(service.findByTenantId(eq(1))).thenReturn(new TenantProfile());
		when(mapper.toPublicDTO(any(TenantProfile.class)))
			.thenReturn(TenantProfilePublicDTO.builder()
				.businessName("LaRoka")
				.description("La mejor pizza de la Patagonia")
				.instagramUrl("https://instagram.com/laroka")
				.build());

		mockMvc.perform(get("/tenants/1/profile"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.businessName").value("LaRoka"))
			.andExpect(jsonPath("$.description").value("La mejor pizza de la Patagonia"))
			.andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/laroka"));
	}

	@Test
	void getProfile_noProfile_returns404() throws Exception {
		when(service.findByTenantId(eq(99))).thenThrow(new TenantProfileNotFoundException(99));

		mockMvc.perform(get("/tenants/99/profile"))
			.andExpect(status().isNotFound());
	}
}
