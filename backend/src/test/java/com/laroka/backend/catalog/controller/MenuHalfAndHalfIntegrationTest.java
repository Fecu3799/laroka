package com.laroka.backend.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-HH-F-01: GET /branches/{id}/menu expone allowsHalfAndHalf por categoría.
 *
 * El flag vive en category_type, dos saltos lazy más allá del producto
 * (product → category → categoryType). El menú se mapea a DTO en el controller, fuera de
 * la transacción del service y sobre entidades que además viajan por el cache Caffeine,
 * con spring.jpa.open-in-view=false: sin el LEFT JOIN FETCH c.categoryType en la query,
 * leer el flag lanzaría LazyInitializationException (500). El test unitario del mapper no
 * lo caza porque arma el CategoryType como entidad concreta, no como proxy — de ahí este
 * test con DB real.
 *
 * Se cubren las tres categorías posibles: con tipo que permite mitad y mitad, con tipo que
 * no lo permite, y sin tipo asignado (FK nullable, categorías previas a US-CAT-02).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MenuHalfAndHalfIntegrationTest {

	private static final int TENANT_ID = 1;
	private static final int BRANCH_ID = 1;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired CacheManager cacheManager;

	@BeforeAll
	void seed() {
		// El menú es @Cacheable y el contexto Spring se comparte entre clases de test: sin
		// limpiar, la sucursal 1 podría responder con el menú sembrado por otro test.
		cacheManager.getCache("menu").clear();
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Test Branch", "Test Address", TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, active) VALUES (?, ?, ?)", "Pizza", true, true);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, active) VALUES (?, ?, ?)", "Bebida", false, true);

		// A: categoría con tipo que permite mitad y mitad.
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		// B: categoría con tipo que no lo permite.
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Bebidas", TENANT_ID, 2);
		// C: categoría sin tipo asignado (category_type_id = NULL).
		jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Sin tipo", TENANT_ID);

		insertProduct("Muzzarella", new BigDecimal("10000.00"), 1, 1);
		insertProduct("Gaseosa", new BigDecimal("3000.00"), 2, 2);
		insertProduct("Postre", new BigDecimal("4000.00"), 3, 3);
	}

	private void insertProduct(String name, BigDecimal price, int categoryId, int productId) {
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			name, price, categoryId, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, productId);
	}

	@Test
	void getMenu_exposesAllowsHalfAndHalfPerCategory() throws Exception {
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			// El orden lo fija la query (ORDER BY c.name): Bebidas, Pizzas, Sin tipo.
			.andExpect(jsonPath("$[?(@.categoryName == 'Pizzas')].allowsHalfAndHalf").value(true))
			.andExpect(jsonPath("$[?(@.categoryName == 'Bebidas')].allowsHalfAndHalf").value(false))
			// Categoría sin tipo: el flag resuelve false sin romper el endpoint.
			.andExpect(jsonPath("$[?(@.categoryName == 'Sin tipo')].allowsHalfAndHalf").value(false));
	}
}
