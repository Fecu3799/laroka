package com.pedisur.backend.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Blindaje de regresión (US-15): PATCH /backoffice/products/{id}/availability retorna
 * bp.getProduct() desde el service y lo mapea a DTO en el controller, fuera de la
 * transacción. Con spring.jpa.open-in-view=false, si findByBranchIdAndProductId no
 * inicializa el product asociado (proxy lazy), ProductMapper.toResponseDTO lanzaba
 * LazyInitializationException: Could not initialize proxy [Product#1] (500). El
 * @EntityGraph({"product"}) sobre esa query lo carga inicializado y el endpoint responde
 * 200. Los tests unitarios del service no lo cazan porque construyen el Product en memoria
 * (entidad concreta), no como proxy Hibernate — de ahí este test con DB real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductAvailabilityIntegrationTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
	private static final int TENANT_ID = 1;
	private static final int BRANCH_ID = 1;
	private static final int MANAGER_USER_ID = 1;
	private static final int PRODUCT_ID = 1;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeAll
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Test Branch", "Test Address", TENANT_ID);
		// Usuario MANAGER activo cuyo id coincide con el subject del token (id auto = 1).
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Manager", "manager@test.com", "noop", "MANAGER", BRANCH_ID);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Pizzas", TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Test Pizza", new BigDecimal("10.00"), 1, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, PRODUCT_ID);
	}

	private String managerToken() {
		return Jwts.builder()
			.subject(String.valueOf(MANAGER_USER_ID))
			.claim("role", "MANAGER")
			.claim("branchId", BRANCH_ID)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	@Test
	void updateAvailability_asManager_returns200_mappingProxyProduct() throws Exception {
		// El branchId lo resuelve SecurityUtils desde el token del MANAGER; el service
		// carga el BranchProduct y retorna su Product para mapear a DTO en el controller.
		mockMvc.perform(patch("/backoffice/products/" + PRODUCT_ID + "/availability")
				.header("Authorization", "Bearer " + managerToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"available\": false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(PRODUCT_ID))
			.andExpect(jsonPath("$.name").value("Test Pizza"))
			// Ids de asociaciones lazy: se leen del proxy sin inicializarlo.
			.andExpect(jsonPath("$.categoryId").value(1))
			.andExpect(jsonPath("$.tenantId").value(TENANT_ID));
	}
}
