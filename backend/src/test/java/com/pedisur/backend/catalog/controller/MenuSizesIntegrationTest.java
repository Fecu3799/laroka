package com.pedisur.backend.catalog.controller;

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
 * US-SIZE-F-02: GET /branches/{id}/menu expone allowsSizes por categoría y los tamaños
 * activos por producto, con el precio efectivo de la sucursal ya resuelto (US-SIZE-02).
 *
 * Escenario sembrado en la sucursal 1:
 *  - Muzzarella: tamaño CHICA con override de sucursal → debe ganar el override.
 *  - Napolitana: tamaño CHICA sin override → debe valer el precio base del tamaño.
 *  - Napolitana: tamaño GRANDE inactivo → no debe aparecer.
 *  - Gaseosa (categoría sin tamaños): lista de tamaños vacía.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MenuSizesIntegrationTest {

	private static final int TENANT_ID = 1;
	private static final int BRANCH_ID = 1;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired CacheManager cacheManager;

	@BeforeAll
	void seed() {
		cacheManager.getCache("menu").clear();
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Test Branch", "Test Address", TENANT_ID);
		// Tipo con tamaños habilitados y otro sin.
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Pizza", true, true, true);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Bebida", false, false, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Bebidas", TENANT_ID, 2);

		insertProduct("Muzzarella", new BigDecimal("15000.00"), 1, 1);
		insertProduct("Napolitana", new BigDecimal("17000.00"), 1, 2);
		insertProduct("Gaseosa", new BigDecimal("3000.00"), 2, 3);

		insertSize(1, "CHICA", new BigDecimal("9000.00"), true);    // id 1 — con override
		insertSize(2, "CHICA", new BigDecimal("11000.00"), true);   // id 2 — sin override
		insertSize(2, "GRANDE", new BigDecimal("17000.00"), false); // id 3 — inactivo

		jdbcTemplate.update(
			"INSERT INTO branch_product_size (branch_id, product_size_id, price_override) VALUES (?, ?, ?)",
			BRANCH_ID, 1, new BigDecimal("9900.00"));
	}

	private void insertProduct(String name, BigDecimal price, int categoryId, int productId) {
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			name, price, categoryId, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, productId);
	}

	private void insertSize(int productId, String size, BigDecimal price, boolean active) {
		jdbcTemplate.update(
			"INSERT INTO product_size (product_id, size, price, active) VALUES (?, ?, ?, ?)",
			productId, size, price, active);
	}

	private static String product(String name) {
		return "$..products[?(@.name == '" + name + "')]";
	}

	@Test
	void getMenu_exposesAllowsSizesPerCategory() throws Exception {
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.categoryName == 'Pizzas')].allowsSizes").value(true))
			.andExpect(jsonPath("$[?(@.categoryName == 'Bebidas')].allowsSizes").value(false));
	}

	@Test
	void getMenu_sizeWithBranchOverride_usesTheOverride() throws Exception {
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(product("Muzzarella") + ".sizes[0].id").value(1))
			.andExpect(jsonPath(product("Muzzarella") + ".sizes[0].size").value("CHICA"))
			// Base 9000, override de sucursal 9900 → manda el override (US-SIZE-02).
			.andExpect(jsonPath(product("Muzzarella") + ".sizes[0].price").value(9900.00));
	}

	@Test
	void getMenu_sizeWithoutOverride_usesBasePrice() throws Exception {
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(product("Napolitana") + ".sizes[0].price").value(11000.00));
	}

	@Test
	void getMenu_inactiveSize_isNotExposed() throws Exception {
		// Napolitana tiene CHICA activo y GRANDE inactivo: sólo debe viajar el activo.
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(product("Napolitana") + ".sizes.length()").value(1))
			.andExpect(jsonPath(product("Napolitana") + ".sizes[0].size").value("CHICA"));
	}

	@Test
	void getMenu_productWithoutSizes_exposesEmptyList() throws Exception {
		mockMvc.perform(get("/branches/" + BRANCH_ID + "/menu"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(product("Gaseosa") + ".sizes.length()").value(0));
	}
}
