package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderStatusHistory;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.repository.WorkShiftRepository;
import com.laroka.backend.shift.service.WorkShiftService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-TEST-01 — Test de integración con DB real (Postgres) del flujo de turnos y
 * recepción de pedidos, el punto de mayor riesgo tras varios refactors
 * (eliminación del bypass por horario, auto-cierre con cancelación en cascada).
 *
 * Postgres real vía @ActiveProfiles("test") (servicio postgres-test, puerto 5433).
 * El único punto externo —el gateway de pago— se reemplaza por @MockitoBean, así
 * ningún test mueve plata real ni hace llamadas de red. El auto-cierre se ejercita
 * invocando {@link WorkShiftService#autoCloseShift(UUID)} directamente (entrada del
 * job de cierre por duración) para que el test sea determinista y no dependa del reloj.
 *
 * Cada test parte de una base limpia (@BeforeEach TRUNCATE ... RESTART IDENTITY)
 * para ser independiente del orden de ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftReceptionFlowIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int BRANCH_ID = 1;
    private static final int MANAGER_USER_ID = 1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderStatusHistoryRepository historyRepository;
    @Autowired WorkShiftRepository workShiftRepository;
    @Autowired WorkShiftService workShiftService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PaymentGateway paymentGateway;

    @BeforeEach
    void resetDatabase() {
        // TRUNCATE desde las tablas raíz: CASCADE limpia todo lo dependiente
        // (orders, work_shift, work_shift_summary, payment, order_status_history…).
        // RESTART IDENTITY reinicia las secuencias para que los primeros inserts
        // obtengan los IDs que el test espera (branch=1, manager=1, product=1).
        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");

        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        // Sucursal activa; accepting_orders arranca en false (default de la columna).
        jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
            "Test Branch", "Test Address", 1);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Manager", "manager@test.com", "noop", "MANAGER", BRANCH_ID);
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
        jdbcTemplate.update(
            "INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Test Pizza", new java.math.BigDecimal("10.00"), 1, 1);
        jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);
    }

    // ── Caso principal: abrir → activar recepción → crear pedido → verificar
    //    asociación al turno → desactivar recepción → 422 → cerrar turno ──────────

    @Test
    void shiftLifecycle_acceptsOrderThenRejectsWhenNotAccepting_thenCloses() throws Exception {
        // 1. Abrir turno.
        UUID shiftId = openShift();
        assertThat(workShiftRepository.findById(shiftId).orElseThrow().getStatus())
            .isEqualTo(ShiftStatus.OPEN);

        // 2. Activar recepción de pedidos (requiere turno abierto).
        assertThat(toggleOrders()).isTrue();

        // 3. Crear pedido → se acepta y queda asociado al turno recién abierto.
        UUID orderId = createOrder("TAKEAWAY", "CASH", null);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        String associatedShiftId = jdbcTemplate.queryForObject(
            "SELECT shift_id FROM orders WHERE id = ?", String.class, orderId);
        assertThat(associatedShiftId).isEqualTo(shiftId.toString());

        // 4. Desactivar recepción → el mismo POST /orders ahora es rechazado con 422.
        assertThat(toggleOrders()).isFalse();
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("TAKEAWAY", "CASH", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El local no está aceptando pedidos en este momento"));

        // 5. Resolver el pedido activo (entregarlo) para poder cerrar el turno.
        //    El pago en efectivo debe confirmarse antes de DELIVERED.
        confirmCashPayment(orderId);
        transition(orderId, "IN_PREPARATION");
        transition(orderId, "READY_FOR_PICKUP");
        transition(orderId, "DELIVERED");

        // 6. Cerrar turno → 200 con summary; el pedido entregado se contabiliza.
        mockMvc.perform(post("/backoffice/shifts/close")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalOrders").value(1))
            .andExpect(jsonPath("$.deliveredOrders").value(1));

        assertThat(workShiftRepository.findById(shiftId).orElseThrow().getStatus())
            .isEqualTo(ShiftStatus.CLOSED);
    }

    // ── Caso adicional: crear pedido sin turno abierto → 422 ────────────────────

    @Test
    void createOrder_withoutOpenShift_returns422() throws Exception {
        // Recepción activada pero SIN turno abierto: forzamos el flag por SQL para
        // pasar la validación de accepting_orders y llegar a la resolución de turno
        // (accepting_orders se valida antes que el turno en OrderService).
        jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("TAKEAWAY", "CASH", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("No hay turno activo para esta sucursal"));
    }

    // ── Caso adicional: abrir turno sobre sucursal desactivada → rechazado ──────

    @Test
    void openShift_onInactiveBranch_isRejected() throws Exception {
        jdbcTemplate.update("UPDATE branch SET active = false WHERE id = ?", BRANCH_ID);

        mockMvc.perform(post("/backoffice/shifts/open")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("No se puede abrir turno en una sucursal desactivada"));

        assertThat(workShiftRepository.findByBranchIdAndStatus(BRANCH_ID, ShiftStatus.OPEN)).isEmpty();
    }

    // ── Caso adicional: auto-cierre con pedidos activos los cancela en cascada,
    //    dispara reembolso, y el summary refleja los pedidos recién cancelados ───

    @Test
    void autoClose_cancelsActiveOrdersInCascade_refundsAndReflectsInSummary() throws Exception {
        UUID shiftId = openShift();
        assertThat(toggleOrders()).isTrue();

        // Pedido DELIVERY con MercadoPago aprobado vía webhook → RECEIVED (activo)
        // con un pago APPROVED que el auto-cierre debe reembolsar en su totalidad.
        String mpPaymentId = "mp-autoclose-001";
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-autoclose-001");

        UUID orderId = createOrder("DELIVERY", "MERCADOPAGO", "Av. Rivadavia 1234");
        initiatePayment(orderId);
        triggerApprovedWebhook(mpPaymentId, orderId);

        Payment approved = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        java.math.BigDecimal totalAmount = orderRepository.findById(orderId).orElseThrow().getTotalAmount();

        // Auto-cierre del turno (entrada del job de cierre por duración máxima).
        workShiftService.autoCloseShift(shiftId);

        // 1. El pedido activo fue cancelado en cascada con el motivo del auto-cierre.
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELLED);
        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        OrderStatusHistory lastCancel = history.stream()
            .filter(h -> h.getToStatus() == OrderStatus.CANCELLED)
            .reduce((first, second) -> second)
            .orElseThrow();
        assertThat(lastCancel.getCancellationReason())
            .isEqualTo(OrderService.SHIFT_AUTO_CLOSE_CANCELLATION_REASON);

        // 2. Se disparó el reembolso TOTAL (amount null) sobre el pago MercadoPago,
        //    dejando el pago en REFUNDED con el monto completo del pedido.
        verify(paymentGateway).refundPayment(mpPaymentId, null);
        Payment refunded = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundedAmount()).isEqualByComparingTo(totalAmount);

        // 3. El turno cerró como auto-cierre (CLOSED, sin closedBy) y no dejó ningún
        //    pedido en estado no-terminal asociado.
        WorkShift closed = workShiftRepository.findById(shiftId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ShiftStatus.CLOSED);
        assertThat(closed.getClosedBy()).isNull();
        Integer nonTerminal = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE shift_id = ? AND status NOT IN ('DELIVERED', 'CANCELLED')",
            Integer.class, shiftId);
        assertThat(nonTerminal).isZero();

        // 4. El summary persistido refleja el pedido recién cancelado.
        Integer totalOrders = jdbcTemplate.queryForObject(
            "SELECT total_orders FROM work_shift_summary WHERE shift_id = ?", Integer.class, shiftId);
        Integer cancelledOrders = jdbcTemplate.queryForObject(
            "SELECT cancelled_orders FROM work_shift_summary WHERE shift_id = ?", Integer.class, shiftId);
        assertThat(totalOrders).isEqualTo(1);
        assertThat(cancelledOrders).isEqualTo(1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

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

    private UUID openShift() throws Exception {
        MvcResult result = mockMvc.perform(post("/backoffice/shifts/open")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andReturn();
        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("shiftId").asText());
    }

    /** Flips accepting_orders y retorna el nuevo valor. */
    private boolean toggleOrders() throws Exception {
        MvcResult result = mockMvc.perform(patch("/backoffice/branches/toggle-orders")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("acceptingOrders").asBoolean();
    }

    private String orderBody(String orderType, String paymentMethod, String deliveryAddress) {
        return String.format("""
            {
                "branchId": %d,
                "orderType": "%s",
                "paymentMethod": "%s"%s,
                "items": [{"productId": 1, "quantity": 1}]
            }
            """,
            BRANCH_ID, orderType, paymentMethod,
            deliveryAddress != null ? ",\"deliveryAddress\":\"" + deliveryAddress + "\"" : "");
    }

    private UUID createOrder(String orderType, String paymentMethod, String deliveryAddress) throws Exception {
        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(orderType, paymentMethod, deliveryAddress)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
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

    private void transition(UUID orderId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"" + nextStatus + "\"}"))
            .andExpect(status().isNoContent());
    }
}
