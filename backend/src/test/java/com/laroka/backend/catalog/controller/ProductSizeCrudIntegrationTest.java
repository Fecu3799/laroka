package com.laroka.backend.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-SIZE-04: blindaje de extremo a extremo de los endpoints de tamaños, contra DB real.
 *
 * Los tests unitarios del service cubren las reglas con mocks; acá se verifica lo que sólo
 * se ve con la pila completa: los códigos HTTP que devuelve el GlobalExceptionHandler, la
 * serialización de los DTOs, y sobre todo el estado que queda en la base — que la baja sea
 * lógica y que limpiar un override borre la fila.
 *
 * Incluye también el GET /branch-config con tamaños (US-SIZE-F-01), que es el camino de
 * lectura que consume el drawer del backoffice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSizeCrudIntegrationTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
	private static final int TENANT_ID = 1;
	private static final int BRANCH_ID = 1;
	private static final int ADMIN_USER_ID = 1;
	private static final int PIZZA_ID = 1;      // categoría con allows_sizes = true
	private static final int GASEOSA_ID = 2;    // categoría sin tamaños

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired CacheManager cacheManager;

	@BeforeEach
	void seed() {
		cacheManager.getCache("menu").clear();
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Centro", "Av. Roca 1", TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Norte", "Av. Roca 2", TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Admin", "admin@test.com", "noop", "ADMIN", BRANCH_ID);
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

		insertProduct("Muzzarella", new BigDecimal("15000.00"), 1, PIZZA_ID);
		insertProduct("Gaseosa", new BigDecimal("3000.00"), 2, GASEOSA_ID);
	}

	private void insertProduct(String name, BigDecimal price, int categoryId, int productId) {
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			name, price, categoryId, TENANT_ID);
		// Un BranchProduct por sucursal, igual que hace el alta real de producto.
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", 1, productId);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", 2, productId);
	}

	private String adminToken() {
		return Jwts.builder()
			.subject(String.valueOf(ADMIN_USER_ID))
			.claim("role", "ADMIN")
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	// Crea el tamaño chica del producto y devuelve su id.
	private int createChica(String price) throws Exception {
		String body = mockMvc.perform(post("/backoffice/products/" + PIZZA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":" + price + "}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.id");
	}

	// ── POST /sizes ─────────────────────────────────────────────────────────────

	@Test
	void createChica_returns201AndPersistsActiveRow() throws Exception {
		mockMvc.perform(post("/backoffice/products/" + PIZZA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":9000.00}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.size").value("CHICA"))
			.andExpect(jsonPath("$.productId").value(PIZZA_ID))
			.andExpect(jsonPath("$.price").value(9000.00))
			.andExpect(jsonPath("$.active").value(true));

		assertThat(countSizes(PIZZA_ID)).isEqualTo(1);
	}

	@Test
	void createGrande_returns422AndPersistsNothing() throws Exception {
		// El grande es implícito: su precio es siempre product.price.
		mockMvc.perform(post("/backoffice/products/" + PIZZA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"GRANDE\",\"price\":15000.00}"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(countSizes(PIZZA_ID)).isZero();
	}

	@Test
	void createDuplicateChica_returns422NotAConstraintError() throws Exception {
		createChica("9000.00");

		mockMvc.perform(post("/backoffice/products/" + PIZZA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":9500.00}"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(countSizes(PIZZA_ID)).isEqualTo(1);
	}

	@Test
	void createOnCategoryWithoutSizes_returns422() throws Exception {
		mockMvc.perform(post("/backoffice/products/" + GASEOSA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":1500.00}"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(countSizes(GASEOSA_ID)).isZero();
	}

	@Test
	void createOnUnknownProduct_returns404() throws Exception {
		mockMvc.perform(post("/backoffice/products/999/sizes")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":9000.00}"))
			.andExpect(status().isNotFound());
	}

	// ── PATCH /sizes/{sizeId} ───────────────────────────────────────────────────

	@Test
	void updatePrice_persistsTheNewValue() throws Exception {
		int sizeId = createChica("9000.00");

		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId)
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"price\":9900.00}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price").value(9900.00))
			.andExpect(jsonPath("$.active").value(true));

		assertThat(priceOf(sizeId)).isEqualByComparingTo("9900.00");
	}

	@Test
	void deactivate_keepsTheRowInDatabase() throws Exception {
		// Baja lógica: order_item.product_size_id referencia estas filas históricamente.
		int sizeId = createChica("9000.00");

		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId)
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		assertThat(countSizes(PIZZA_ID)).isEqualTo(1);
		assertThat(isActive(sizeId)).isFalse();
	}

	@Test
	void updateSizeOfAnotherProduct_returns422() throws Exception {
		int sizeId = createChica("9000.00");

		mockMvc.perform(patch("/backoffice/products/" + GASEOSA_ID + "/sizes/" + sizeId)
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"price\":1.00}"))
			.andExpect(status().isUnprocessableEntity());

		assertThat(priceOf(sizeId)).isEqualByComparingTo("9000.00");
	}

	@Test
	void updateUnknownSize_returns404() throws Exception {
		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/999")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"price\":9900.00}"))
			.andExpect(status().isNotFound());
	}

	// ── GET /sizes ──────────────────────────────────────────────────────────────

	@Test
	void getSizes_includesInactiveOnes() throws Exception {
		// A diferencia del menú del client, el backoffice ve el tamaño dado de baja para
		// poder reactivarlo.
		int sizeId = createChica("9000.00");
		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId)
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/backoffice/products/" + PIZZA_ID + "/sizes")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].active").value(false));
	}

	// ── PATCH /sizes/{sizeId}/branch-config ─────────────────────────────────────

	@Test
	void setBranchOverride_returns204AndPersistsTheRow() throws Exception {
		int sizeId = createChica("9000.00");

		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId + "/branch-config")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1,\"priceOverride\":9900.00}"))
			.andExpect(status().isNoContent());

		assertThat(overrideOf(BRANCH_ID, sizeId)).isEqualByComparingTo("9900.00");
	}

	@Test
	void clearBranchOverride_deletesTheRowInsteadOfLeavingItNull() throws Exception {
		// Sin fila y con fila de override nulo son estados equivalentes (US-SIZE-02).
		int sizeId = createChica("9000.00");
		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId + "/branch-config")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1,\"priceOverride\":9900.00}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId + "/branch-config")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1}"))
			.andExpect(status().isNoContent());

		assertThat(countOverrides(sizeId)).isZero();
	}

	// ── GET /branch-config con tamaños (US-SIZE-F-01) ───────────────────────────

	@Test
	void branchConfig_exposesSizePricePerBranch() throws Exception {
		int sizeId = createChica("9000.00");
		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId + "/branch-config")
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1,\"priceOverride\":9900.00}"))
			.andExpect(status().isNoContent());

		// Centro con override, Norte con el precio base del tamaño.
		mockMvc.perform(get("/backoffice/products/" + PIZZA_ID + "/branch-config")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.branchName == 'Centro')].productSizeId").value(sizeId))
			.andExpect(jsonPath("$[?(@.branchName == 'Centro')].sizePriceOverride").value(9900.00))
			.andExpect(jsonPath("$[?(@.branchName == 'Centro')].sizeEffectivePrice").value(9900.00))
			// Un path con filtro devuelve una lista, así que un campo nulo llega como [null] y
			// doesNotExist() no aplica: se afirma el null explícitamente.
			.andExpect(jsonPath("$[?(@.branchName == 'Norte')].sizePriceOverride").value((Object) null))
			.andExpect(jsonPath("$[?(@.branchName == 'Norte')].sizeEffectivePrice").value(9000.00));
	}

	@Test
	void branchConfig_productWithoutSizes_leavesSizeFieldsNull() throws Exception {
		mockMvc.perform(get("/backoffice/products/" + GASEOSA_ID + "/branch-config")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].productSizeId").doesNotExist())
			.andExpect(jsonPath("$[0].sizeEffectivePrice").doesNotExist())
			// El precio del producto sigue estando: sólo los campos del tamaño son null.
			.andExpect(jsonPath("$[0].effectivePrice").value(3000.00));
	}

	@Test
	void branchConfig_inactiveSize_isNotExposed() throws Exception {
		// Un tamaño dado de baja no debe aparecer como columna editable en el drawer.
		int sizeId = createChica("9000.00");
		mockMvc.perform(patch("/backoffice/products/" + PIZZA_ID + "/sizes/" + sizeId)
				.header("Authorization", "Bearer " + adminToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/backoffice/products/" + PIZZA_ID + "/branch-config")
				.header("Authorization", "Bearer " + adminToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].productSizeId").doesNotExist());
	}

	// ── Helpers de verificación contra la base ──────────────────────────────────

	private Integer countSizes(int productId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM product_size WHERE product_id = ?", Integer.class, productId);
	}

	private BigDecimal priceOf(int sizeId) {
		return jdbcTemplate.queryForObject(
			"SELECT price FROM product_size WHERE id = ?", BigDecimal.class, sizeId);
	}

	private Boolean isActive(int sizeId) {
		return jdbcTemplate.queryForObject(
			"SELECT active FROM product_size WHERE id = ?", Boolean.class, sizeId);
	}

	private Integer countOverrides(int sizeId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM branch_product_size WHERE product_size_id = ?", Integer.class, sizeId);
	}

	private BigDecimal overrideOf(int branchId, int sizeId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"SELECT price_override FROM branch_product_size WHERE branch_id = ? AND product_size_id = ?",
			branchId, sizeId);
		return rows.isEmpty() ? null : (BigDecimal) rows.get(0).get("price_override");
	}
}
