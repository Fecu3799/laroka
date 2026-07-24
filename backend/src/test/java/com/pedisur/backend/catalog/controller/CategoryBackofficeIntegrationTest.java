package com.pedisur.backend.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Blindaje de regresión: el listado de categorías del backoffice se mapea a DTO en
 * el controller, fuera de la transacción del service. Con spring.jpa.open-in-view=false
 * (base application.yml, heredado por el perfil test), acceder a una asociación lazy
 * del tenant en ese punto lanzaba LazyInitializationException (500). Al exponer solo
 * tenantId (sin el objeto Tenant anidado), el acceso al id no inicializa el proxy y el
 * endpoint responde 200. Este bug no lo cazan los tests unitarios del mapper porque
 * construyen el Tenant como entidad concreta, no como proxy lazy — de ahí este test
 * de integración con DB real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryBackofficeIntegrationTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
	private static final int TENANT_ID = 1;
	private static final int ADMIN_USER_ID = 1;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeAll
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product, product, category, category_type, staff_user, branch, tenant "
				+ "RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Test Branch", "Test Address", TENANT_ID);
		// Usuario ADMIN activo cuyo id coincide con el subject del token (id auto = 1).
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Admin", "admin@test.com", "noop", "ADMIN", 1);
		// US-CAT-03: un tipo activo y uno inactivo (para verificar el filtro del endpoint).
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, active) VALUES (?, ?, ?)", "Pizza", true, true);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, active) VALUES (?, ?, ?)", "Descontinuada", false, false);
		// Categoría con tipo asignado (category_type_id = 1) — cubre el mapeo del categoryType lazy.
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
	}

	private String adminToken() {
		return Jwts.builder()
			.subject(String.valueOf(ADMIN_USER_ID))
			.claim("role", "ADMIN")
			.claim("tenantId", TENANT_ID)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	@Test
	void listCategoriesByTenant_asAdmin_returns200_withTenantIdAndNoNestedTenant() throws Exception {
		mockMvc.perform(get("/backoffice/categories?tenantId=" + TENANT_ID)
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Pizzas"))
			.andExpect(jsonPath("$[0].tenantId").value(TENANT_ID))
			.andExpect(jsonPath("$[0].tenant").doesNotExist());
	}

	@Test
	void listCategories_asAdmin_exposesCategoryTypeIdAndName_withoutLazyInitError() throws Exception {
		// US-CAT-03: la categoría tiene category_type_id asignado. El mapper lee
		// categoryType.name fuera de sesión; sin el @EntityGraph esto lanzaría
		// LazyInitializationException (500). Debe responder 200 con id y name del tipo.
		mockMvc.perform(get("/backoffice/categories?tenantId=" + TENANT_ID)
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].categoryTypeId").value(1))
			.andExpect(jsonPath("$[0].categoryTypeName").value("Pizza"));
	}

	@Test
	void listCategoryTypes_asAdmin_returnsOnlyActiveTypes() throws Exception {
		// US-CAT-03: GET /backoffice/category-types lista solo los tipos activos.
		mockMvc.perform(get("/backoffice/category-types")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Pizza"))
			.andExpect(jsonPath("$[0].allowsHalfAndHalf").value(true));
	}

	@Test
	void listAllCategories_asAdmin_returns200_withTenantIdAndNoNestedTenant() throws Exception {
		// Ruta findAll() (sin filtro por tenant): mismo mapeo, mismo riesgo de proxy lazy.
		mockMvc.perform(get("/backoffice/categories")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tenantId").value(TENANT_ID))
			.andExpect(jsonPath("$[0].tenant").doesNotExist());
	}
}
