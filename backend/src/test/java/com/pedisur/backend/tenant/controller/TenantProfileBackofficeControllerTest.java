package com.pedisur.backend.tenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pedisur.backend.shared.security.JwtService;
import com.pedisur.backend.shared.security.TokenBlacklist;
import com.pedisur.backend.staffuser.service.StaffUserService;
import com.pedisur.backend.tenant.mapper.TenantProfileMapper;
import com.pedisur.backend.tenant.service.TenantProfileService;

@WebMvcTest(controllers = TenantProfileBackofficeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantProfileBackofficeControllerTest {

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
	void upsert_missingBusinessName_returns400() throws Exception {
		String body = "{\"description\":\"desc\"}";

		mockMvc.perform(put("/backoffice/tenant/profile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());

		verify(service, never()).upsert(any(), any());
	}

	@Test
	void upsert_missingDescription_returns400() throws Exception {
		String body = "{\"businessName\":\"LaRoka\"}";

		mockMvc.perform(put("/backoffice/tenant/profile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());

		verify(service, never()).upsert(any(), any());
	}
}
