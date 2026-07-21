package com.laroka.backend.order.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * El backoffice expone el tamaño del ítem en BackofficeOrderItemDTO, igual que ya exponía la
 * segunda mitad.
 *
 * El punto del test es el grafo de carga: los ítems se mapean a DTO en el controller, fuera
 * de la transacción y con spring.jpa.open-in-view=false. productSize no estaba en el
 * @EntityGraph de las queries de pedidos, así que leer su nombre dependía de un lazy load
 * después de cerrada la sesión. El test unitario del mapper no lo caza porque arma el
 * ProductSize como entidad concreta, no como proxy — de ahí este test con DB real.
 *
 * Cubre los dos endpoints que alimentan las pantallas del operador: el listado y el detalle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackofficeOrderSizeIntegrationTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
	private static final int TENANT_ID = 1;
	private static final int BRANCH_ID = 1;
	private static final int STAFF_USER_ID = 1;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeAll
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE branch_product_size, product_size, branch_product, product, category, "
				+ "category_type, staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
			"Centro", "Av. Roca 1", TENANT_ID);
		jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Staff", "staff@test.com", "noop", "STAFF", BRANCH_ID);
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Pizza", true, true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Muzzarella", new BigDecimal("15000.00"), 1, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);
		jdbcTemplate.update(
			"INSERT INTO product_size (product_id, size, price, active) VALUES (?, ?, ?, ?)",
			1, "CHICA", new BigDecimal("9000.00"), true);
		jdbcTemplate.update(
			"INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) "
				+ "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
			UUID.randomUUID(), BRANCH_ID, STAFF_USER_ID);
	}

	private String staffToken() {
		return Jwts.builder()
			.subject(String.valueOf(STAFF_USER_ID))
			.claim("role", "STAFF")
			.claim("branchId", BRANCH_ID)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	private UUID createOrder(String items) throws Exception {
		String body = """
			{
				"branchId": %d,
				"orderType": "TAKEAWAY",
				"paymentMethod": "CASH",
				"items": %s
			}
			""".formatted(BRANCH_ID, items);
		MvcResult result = mockMvc.perform(post("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(
			objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
	}

	@Test
	void orderDetail_sizedItem_exposesSizeName() throws Exception {
		UUID orderId = createOrder("[{\"productId\":1,\"productSizeId\":1,\"quantity\":1}]");

		mockMvc.perform(get("/backoffice/orders/" + orderId)
				.header("Authorization", "Bearer " + staffToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].productName").value("Muzzarella"))
			.andExpect(jsonPath("$.items[0].sizeName").value("CHICA"))
			// US-SIZE-02: el precio del ítem es el del tamaño, no el del producto.
			.andExpect(jsonPath("$.items[0].unitPrice").value(9000.00));
	}

	@Test
	void orderDetail_itemWithoutSize_leavesSizeNameNull() throws Exception {
		// El grande es implícito: se pide sin productSizeId y el campo llega vacío.
		UUID orderId = createOrder("[{\"productId\":1,\"quantity\":1}]");

		mockMvc.perform(get("/backoffice/orders/" + orderId)
				.header("Authorization", "Bearer " + staffToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].sizeName").doesNotExist())
			.andExpect(jsonPath("$.items[0].unitPrice").value(15000.00));
	}

	@Test
	void orderList_sizedItem_exposesSizeName() throws Exception {
		// El listado usa otra query (findActiveByBranchId): su grafo también debe traer el
		// tamaño, o la columna de productos del listado rompería con lazy loading.
		createOrder("[{\"productId\":1,\"productSizeId\":1,\"quantity\":2}]");

		mockMvc.perform(get("/backoffice/orders")
				.header("Authorization", "Bearer " + staffToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.items[0].sizeName == 'CHICA')]").exists());
	}
}
