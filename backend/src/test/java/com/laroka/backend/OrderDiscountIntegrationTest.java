package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.payment.gateway.PaymentGateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-19-01 / US-19-02 — Test de integración con DB real (Postgres) del descuento
 * porcentual manual.
 *
 * Los tests unitarios de OrderService mockean el repositorio, así que verifican el
 * cálculo pero no que la fila llegue a la base: nada ejercita la migración V39, el
 * mapeo de la entidad, la FK a staff_user ni el CHECK del porcentaje. Este test
 * cubre ese tramo end-to-end, desde el endpoint del backoffice hasta el SQL.
 *
 * Postgres real vía @ActiveProfiles("test") (servicio postgres-test, puerto 5433).
 * El gateway de pago se reemplaza por @MockitoBean: ningún test mueve plata real.
 * Cada test parte de una base limpia (@BeforeEach TRUNCATE ... RESTART IDENTITY).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderDiscountIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int BRANCH_ID = 1;
    private static final int MANAGER_USER_ID = 1;
    private static final int STAFF_USER_ID = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PaymentGateway paymentGateway;

    @BeforeEach
    void resetDatabase() throws Exception {
        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");

        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        // Fees != 0 a propósito: el descuento se calcula sobre el subtotal y los fees
        // se cobran enteros. Con fees en 0 ese comportamiento sería indistinguible.
        jdbcTemplate.update(
            "INSERT INTO branch (name, address, tenant_id, delivery_fee, service_fee) VALUES (?, ?, ?, ?, ?)",
            "Test Branch", "Test Address", 1, new BigDecimal("5.00"), new BigDecimal("2.00"));
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Manager", "manager@test.com", "noop", "MANAGER", BRANCH_ID);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Staff", "staff@test.com", "noop", "STAFF", BRANCH_ID);
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
        jdbcTemplate.update(
            "INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Test Pizza", new BigDecimal("100.00"), 1, 1);
        jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);

        openShift();
        enableOrders();
    }

    // ── Camino feliz: la fila llega a la base y el total queda sobrescrito ───────

    @Test
    void applyDiscount_persistsRowInDatabaseAndOverwritesOrderTotal() throws Exception {
        // DELIVERY: subtotal 100 + envío 5 + servicio 2 = 107.
        UUID orderId = createCashOrder();
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("107.00");

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", "cortesía por demora")
            .andExpect(status().isNoContent());

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM order_discount WHERE order_id = ?", orderId);

        assertThat((BigDecimal) row.get("percentage")).isEqualByComparingTo("10.00");
        // El descuento es 10% del SUBTOTAL (100), no del total: los fees se cobran enteros.
        assertThat((BigDecimal) row.get("discount_amount")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) row.get("original_total_amount")).isEqualByComparingTo("107.00");
        assertThat((BigDecimal) row.get("final_total_amount")).isEqualByComparingTo("97.00");
        assertThat(row.get("reason")).isEqualTo("CUSTOMER_PROMO");
        assertThat(row.get("note")).isEqualTo("cortesía por demora");
        assertThat(row.get("applied_by")).isEqualTo(MANAGER_USER_ID);
        assertThat(row.get("applied_at")).isNotNull();

        // El total del pedido quedó sobrescrito con el final de la fila insertada.
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("97.00");
    }

    @Test
    void applyDiscount_withoutNote_persistsNullNote() throws Exception {
        UUID orderId = createCashOrder();

        applyDiscount(orderId, "50", "OTHER", null).andExpect(status().isNoContent());

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM order_discount WHERE order_id = ?", orderId);
        assertThat(row.get("note")).isNull();
        assertThat((BigDecimal) row.get("final_total_amount")).isEqualByComparingTo("57.00");
    }

    // ── Append-only: la segunda aplicación agrega fila, no muta la anterior ──────

    @Test
    void applyDiscount_twice_appendsSecondRowAndLeavesFirstUntouched() throws Exception {
        UUID orderId = createCashOrder();

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", "primera").andExpect(status().isNoContent());
        applyDiscount(orderId, "20", "TRANSFER_ADJUSTMENT", "segunda").andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            // percentage como desempate: dos POST consecutivos podrían caer en el mismo
            // microsegundo y volver ambiguo el orden por applied_at.
            "SELECT * FROM order_discount WHERE order_id = ? ORDER BY applied_at ASC, percentage ASC",
            orderId);

        assertThat(rows).hasSize(2);
        // La primera fila sobrevive intacta: es la traza de auditoría.
        assertThat((BigDecimal) rows.get(0).get("percentage")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) rows.get(0).get("final_total_amount")).isEqualByComparingTo("97.00");
        assertThat(rows.get(0).get("note")).isEqualTo("primera");

        // La segunda parte de subtotal+fees, NO del total ya descontado: 107 - 20 = 87.
        // Si encadenara sobre 97 daría 77, y el descuento dejaría de ser reproducible.
        assertThat((BigDecimal) rows.get(1).get("original_total_amount")).isEqualByComparingTo("107.00");
        assertThat((BigDecimal) rows.get(1).get("final_total_amount")).isEqualByComparingTo("87.00");

        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("87.00");
    }

    // ── US-19-03: el detalle expone el descuento vigente y su traza ──────────────

    @Test
    void orderDetail_afterDiscount_exposesCurrentDiscountWithTraceability() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", "cortesía por demora")
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/backoffice/orders/" + orderId)
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount.percentage").value(10.00))
            .andExpect(jsonPath("$.discount.originalTotalAmount").value(107.00))
            .andExpect(jsonPath("$.discount.discountAmount").value(10.00))
            .andExpect(jsonPath("$.discount.finalTotalAmount").value(97.00))
            .andExpect(jsonPath("$.discount.reason").value("CUSTOMER_PROMO"))
            .andExpect(jsonPath("$.discount.note").value("cortesía por demora"))
            // El nombre se resuelve desde staff_user: appliedBy es sólo un id.
            .andExpect(jsonPath("$.discount.appliedByName").value("Manager"))
            .andExpect(jsonPath("$.discount.appliedAt").exists())
            // El total de arriba ya viene post-descuento; la línea explica ese número.
            .andExpect(jsonPath("$.totalAmount").value(97.00))
            .andExpect(jsonPath("$.subtotal").value(100.00));
    }

    @Test
    void orderDetail_withoutDiscount_leavesDiscountNull() throws Exception {
        UUID orderId = createCashOrder();

        mockMvc.perform(get("/backoffice/orders/" + orderId)
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").doesNotExist())
            .andExpect(jsonPath("$.totalAmount").value(107.00));
    }

    /**
     * La tabla es append-only: tras dos aplicaciones el detalle muestra sólo la más
     * reciente (findFirstByOrderIdOrderByAppliedAtDesc), no la primera ni una suma.
     */
    @Test
    void orderDetail_afterTwoDiscounts_exposesOnlyTheMostRecentOne() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", "primera").andExpect(status().isNoContent());
        applyDiscount(orderId, "20", "TRANSFER_ADJUSTMENT", "segunda").andExpect(status().isNoContent());

        // La fila anterior sigue en la tabla y con sus valores originales: el detalle
        // muestra sólo la vigente, pero el historial no se borra ni se pisa. El conteo
        // por sí solo no alcanzaría — dejaría pasar una mutación de la fila vieja.
        assertThat(countDiscounts(orderId)).isEqualTo(2);
        Map<String, Object> previous = jdbcTemplate.queryForMap(
            "SELECT * FROM order_discount WHERE order_id = ? AND percentage = 10.00", orderId);
        assertThat((BigDecimal) previous.get("discount_amount")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) previous.get("final_total_amount")).isEqualByComparingTo("97.00");
        assertThat(previous.get("reason")).isEqualTo("CUSTOMER_PROMO");
        assertThat(previous.get("note")).isEqualTo("primera");

        mockMvc.perform(get("/backoffice/orders/" + orderId)
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount.percentage").value(20.00))
            .andExpect(jsonPath("$.discount.reason").value("TRANSFER_ADJUSTMENT"))
            .andExpect(jsonPath("$.discount.note").value("segunda"))
            .andExpect(jsonPath("$.discount.finalTotalAmount").value(87.00))
            .andExpect(jsonPath("$.totalAmount").value(87.00));
    }

    // ── Guards de negocio contra la base real ───────────────────────────────────

    @Test
    void applyDiscount_onMercadoPagoApprovedOrder_returns422AndWritesNothing() throws Exception {
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-discount-001");

        UUID orderId = createMercadoPagoOrder();
        initiatePayment(orderId);
        triggerApprovedWebhook("mp-discount-001", orderId);

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "No se puede aplicar un descuento a un pedido pagado por MercadoPago o QR"));

        assertThat(countDiscounts(orderId)).isZero();
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("107.00");
    }

    /**
     * Con el pago iniciado pero sin aprobar, el pedido sigue en PENDING_PAYMENT: gana
     * el guard de ventana, no el de gateway. Fija cuál de los dos responde.
     */
    @Test
    void applyDiscount_onOrderWithPaymentStillPending_returns422OnWindowGuard() throws Exception {
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-discount-002");

        UUID orderId = createMercadoPagoOrder();
        initiatePayment(orderId);

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "No se puede descontar un pedido con el pago pendiente"));

        assertThat(countDiscounts(orderId)).isZero();
    }

    @Test
    void applyDiscount_onDeliveredOrder_returns422AndWritesNothing() throws Exception {
        UUID orderId = createCashOrder();
        confirmCashPayment(orderId);
        transition(orderId, "IN_PREPARATION");
        transition(orderId, "ON_THE_WAY");
        transition(orderId, "DELIVERED");

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "No se puede descontar un pedido ya entregado: su total ya se factura en el resumen del turno"));

        assertThat(countDiscounts(orderId)).isZero();
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("107.00");
    }

    // ── US-19-04: el resumen de turno refleja el total ya descontado ─────────────

    /**
     * El descuento sobrescribe {@code order.totalAmount} y la ventana se cierra en
     * DELIVERED, así que cuando calculateSummary suma los entregados ya lee el importe
     * final. Este test fija esa cadena end-to-end: sin doble conteo (no se resta el
     * descuento otra vez) y sin inconsistencia entre totalRevenue, revenueByMethod y
     * el averageTicket derivado.
     */
    @Test
    void shiftSummary_afterDiscount_billsTheDiscountedTotalExactlyOnce() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // 107 - 10 = 97. Ni 107 (ignorar el descuento) ni 87 (restarlo dos veces).
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("97.00");
        assertThat((BigDecimal) summary.get("average_ticket")).isEqualByComparingTo("97.00");
        assertThat(summary.get("delivered_orders")).isEqualTo(1);

        // El desglose por método sale del mismo totalAmount: no puede divergir del total.
        assertThat((BigDecimal) summary.get("cash_revenue")).isEqualByComparingTo("97.00");
        assertThat((BigDecimal) summary.get("mp_revenue")).isEqualByComparingTo("0.00");
    }

    @Test
    void shiftSummary_withZeroPercentDiscount_billsTheFullTotal() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "0", "OTHER", "sin ajuste").andExpect(status().isNoContent());
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // 0% es un descuento válido y deja el total intacto; la fila igual queda como traza.
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("107.00");
        assertThat(countDiscounts(orderId)).isEqualTo(1);
    }

    @Test
    void shiftSummary_withHundredPercentDiscount_billsOnlyTheFees() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "100", "CUSTOMER_PROMO", "cortesía total")
            .andExpect(status().isNoContent());
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // El 100% se aplica al subtotal, no a los fees: quedan envío 5 + servicio 2.
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("7.00");
    }

    /**
     * Redondeo: 100 * 33.33% = 33.33 exacto en la fila, y el resumen suma ese mismo
     * importe sin re-redondear (un segundo redondeo desplazaría la caja).
     */
    @Test
    void shiftSummary_withRoundedDiscount_billsTheSameAmountPersistedInTheRow() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "33.33", "TRANSFER_ADJUSTMENT", null)
            .andExpect(status().isNoContent());

        BigDecimal persistedFinal = jdbcTemplate.queryForObject(
            "SELECT final_total_amount FROM order_discount WHERE order_id = ?",
            BigDecimal.class, orderId);
        assertThat(persistedFinal).isEqualByComparingTo("73.67"); // 107 - 33.33

        deliver(orderId);
        Map<String, Object> summary = closeShiftAndReadSummary();

        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo(persistedFinal);
    }

    /** Dos pedidos, uno con descuento y otro sin: la caja suma cada uno por su total. */
    @Test
    void shiftSummary_mixesDiscountedAndUndiscountedOrders() throws Exception {
        UUID discounted = createCashOrder();
        applyDiscount(discounted, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(discounted);

        UUID plain = createCashOrder();
        deliver(plain);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // 97 + 107 = 204, con un ticket promedio de 102.
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("204.00");
        assertThat((BigDecimal) summary.get("average_ticket")).isEqualByComparingTo("102.00");
        assertThat(summary.get("delivered_orders")).isEqualTo(2);
    }

    // ── Autorización y validación sobre la pila real ────────────────────────────

    @Test
    void applyDiscount_asStaff_returns403AndWritesNothing() throws Exception {
        UUID orderId = createCashOrder();

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/discount")
                .header("Authorization", "Bearer " + tokenFor(STAFF_USER_ID, "STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percentage\":10,\"reason\":\"CUSTOMER_PROMO\"}"))
            .andExpect(status().isForbidden());

        assertThat(countDiscounts(orderId)).isZero();
    }

    @Test
    void applyDiscount_withPercentageOutOfRange_returns400AndWritesNothing() throws Exception {
        UUID orderId = createCashOrder();

        applyDiscount(orderId, "150", "CUSTOMER_PROMO", null)
            .andExpect(status().isBadRequest());

        assertThat(countDiscounts(orderId)).isZero();
    }

    // ── La migración protege la tabla aunque se escriba por fuera del service ────

    @Test
    void orderDiscountTable_rejectsPercentageOutOfRangeAtDatabaseLevel() throws Exception {
        UUID orderId = createCashOrder();

        // El CHECK de V39 es la última línea de defensa: una carga manual por SQL
        // (TablePlus) tampoco puede dejar un porcentaje inválido en la tabla.
        assertThatThrownBy(() -> insertDiscountBySql(orderId, new BigDecimal("150.00"), MANAGER_USER_ID))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDiscountBySql(orderId, new BigDecimal("-1.00"), MANAGER_USER_ID))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countDiscounts(orderId)).isZero();
    }

    @Test
    void orderDiscountTable_rejectsUnknownStaffUser() throws Exception {
        UUID orderId = createCashOrder();

        // FK a staff_user: applied_by siempre apunta a un usuario real, para que la
        // traza de "quién aplicó el descuento" no pueda quedar colgada.
        assertThatThrownBy(() -> insertDiscountBySql(orderId, new BigDecimal("10.00"), 9999))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String tokenFor(int userId, String role) {
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .claim("branchId", BRANCH_ID)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private String managerToken() {
        return tokenFor(MANAGER_USER_ID, "MANAGER");
    }

    private void openShift() throws Exception {
        mockMvc.perform(post("/backoffice/shifts/open")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk());
    }

    private void enableOrders() throws Exception {
        mockMvc.perform(patch("/backoffice/branches/toggle-orders")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk());
    }

    private UUID createOrder(String paymentMethod) throws Exception {
        String body = String.format("""
            {
                "branchId": %d,
                "orderType": "DELIVERY",
                "paymentMethod": "%s",
                "deliveryAddress": "Av. Rivadavia 1234",
                "items": [{"productId": 1, "quantity": 1}]
            }
            """, BRANCH_ID, paymentMethod);

        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
    }

    /** Pedido en efectivo: nace ya en RECEIVED con un Payment CASH PENDING. */
    private UUID createCashOrder() throws Exception {
        return createOrder("CASH");
    }

    private UUID createMercadoPagoOrder() throws Exception {
        return createOrder("MERCADOPAGO");
    }

    private org.springframework.test.web.servlet.ResultActions applyDiscount(
            UUID orderId, String percentage, String reason, String note) throws Exception {
        String body = note == null
            ? String.format("{\"percentage\":%s,\"reason\":\"%s\"}", percentage, reason)
            : String.format("{\"percentage\":%s,\"reason\":\"%s\",\"note\":\"%s\"}", percentage, reason, note);

        return mockMvc.perform(post("/backoffice/orders/" + orderId + "/discount")
            .header("Authorization", "Bearer " + managerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private void insertDiscountBySql(UUID orderId, BigDecimal percentage, int appliedBy) {
        jdbcTemplate.update("""
            INSERT INTO order_discount
                (id, order_id, percentage, original_total_amount, discount_amount,
                 final_total_amount, reason, applied_by, applied_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            UUID.randomUUID(), orderId, percentage,
            new BigDecimal("107.00"), new BigDecimal("10.00"), new BigDecimal("97.00"),
            "CUSTOMER_PROMO", appliedBy);
    }

    private int countDiscounts(UUID orderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_discount WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private void initiatePayment(UUID orderId) throws Exception {
        mockMvc.perform(post("/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isOk());
    }

    private void triggerApprovedWebhook(String mpPaymentId, UUID orderId) throws Exception {
        when(paymentGateway.fetchPayment(mpPaymentId))
            .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));
        mockMvc.perform(post("/payments/webhook")
                .param("data.id", mpPaymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"type\":\"payment\",\"data\":{\"id\":\"%s\"}}", mpPaymentId)))
            .andExpect(status().isOk());
    }

    private void confirmCashPayment(UUID orderId) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/payment")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CONFIRM\"}"))
            .andExpect(status().isOk());
    }

    /** Lleva un pedido DELIVERY en efectivo hasta DELIVERED, cobrándolo primero. */
    private void deliver(UUID orderId) throws Exception {
        confirmCashPayment(orderId);
        transition(orderId, "IN_PREPARATION");
        transition(orderId, "ON_THE_WAY");
        transition(orderId, "DELIVERED");
    }

    /** Cierra el turno abierto y devuelve la fila de work_shift_summary persistida. */
    private Map<String, Object> closeShiftAndReadSummary() throws Exception {
        // El cierre exige la recepción desactivada (WorkShiftService.closeShift).
        jdbcTemplate.update("UPDATE branch SET accepting_orders = false WHERE id = ?", BRANCH_ID);
        mockMvc.perform(post("/backoffice/shifts/close")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk());
        return jdbcTemplate.queryForMap("SELECT * FROM work_shift_summary");
    }

    private void transition(UUID orderId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"" + nextStatus + "\"}"))
            .andExpect(status().isNoContent());
    }
}
