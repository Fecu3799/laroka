package com.laroka.backend.order.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

/**
 * US-HH-F-02: GET /orders/{id}/items expone secondProductName para que el banner de
 * seguimiento del client muestre "½ A + ½ B".
 *
 * Cubre el mapeo contra la DB real, con el ítem combinado creado por el endpoint público
 * (no armado a mano): los ítems se mapean a DTO en el controller, fuera de la transacción del
 * service y con spring.jpa.open-in-view=false.
 *
 * Nota: el LEFT JOIN FETCH de secondProduct en findByOrderIdWithProduct evita un SELECT extra
 * por ítem combinado, pero no es un fix de LazyInitializationException — verificado quitándolo:
 * el test sigue pasando, sólo sube de 2 a 3 los SELECT sobre product.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderItemsHalfAndHalfIntegrationTest {

	private static final int BRANCH_ID = 1;
	private static final int TENANT_ID = 1;

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
			"Test Branch", "Test Address", TENANT_ID);
		jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);
		jdbcTemplate.update(
			"INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
			"Staff", "staff@test.com", "noop", "STAFF", BRANCH_ID);
		// La categoría permite mitad y mitad (US-HH-02 lo valida al crear el pedido).
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, active) VALUES (?, ?, ?)", "Pizza", true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		insertProduct("Muzzarella", new BigDecimal("2800.00"), 1);
		insertProduct("Calabresa", new BigDecimal("3400.00"), 2);
		jdbcTemplate.update(
			"INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) "
				+ "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
			UUID.randomUUID(), BRANCH_ID, 1);
	}

	private void insertProduct(String name, BigDecimal price, int productId) {
		jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			name, price, 1, TENANT_ID);
		jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, productId);
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
	void getOrderItems_halfAndHalfItem_exposesBothHalves() throws Exception {
		UUID orderId = createOrder("[{\"productId\":1,\"secondProductId\":2,\"quantity\":1}]");

		mockMvc.perform(get("/orders/" + orderId + "/items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Muzzarella"))
			.andExpect(jsonPath("$[0].secondProductName").value("Calabresa"))
			// US-HH-03: el ítem combinado vale el mayor de las dos mitades.
			.andExpect(jsonPath("$[0].unitPrice").value(3400.00));
	}

	@Test
	void getOrderItems_simpleItem_leavesSecondProductNameNull() throws Exception {
		UUID orderId = createOrder("[{\"productId\":1,\"quantity\":2}]");

		mockMvc.perform(get("/orders/" + orderId + "/items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Muzzarella"))
			.andExpect(jsonPath("$[0].secondProductName").doesNotExist())
			.andExpect(jsonPath("$[0].sizeName").doesNotExist())
			.andExpect(jsonPath("$[0].quantity").value(2));
	}

	// El mismo endpoint expone el tamaño, para que el banner de seguimiento muestre el ítem
	// igual que el carrito antes de confirmar.

	@Test
	void getOrderItems_sizedItem_exposesSizeName() throws Exception {
		jdbcTemplate.update(
			"INSERT INTO product_size (product_id, size, price, active) VALUES (?, ?, ?, ?)",
			1, "CHICA", new BigDecimal("1900.00"), true);
		Integer sizeId = jdbcTemplate.queryForObject(
			"SELECT id FROM product_size WHERE product_id = 1 AND size = 'CHICA'", Integer.class);

		UUID orderId = createOrder(
			"[{\"productId\":1,\"productSizeId\":" + sizeId + ",\"quantity\":1}]");

		mockMvc.perform(get("/orders/" + orderId + "/items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Muzzarella"))
			.andExpect(jsonPath("$[0].sizeName").value("CHICA"))
			.andExpect(jsonPath("$[0].secondProductName").doesNotExist())
			// US-SIZE-02: el precio del ítem es el del tamaño, no el del producto.
			.andExpect(jsonPath("$[0].unitPrice").value(1900.00));
	}
}
