package com.pedisur.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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
import com.pedisur.backend.order.repository.OrderRepository;
import com.pedisur.backend.payment.gateway.PaymentGateway;

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

    // ── US-19-06: revertir un descuento aplicado ─────────────────────────────────

    /**
     * Revertir no borra: inserta una fila REVERTED y el pedido vuelve a su total sin
     * descontar. La fila aplicada anterior sigue en la tabla (traza append-only) y el
     * detalle deja de exponer el descuento — no porque no haya filas, sino porque la
     * vigente es REVERTED. Esa es la distinción que pide el enunciado.
     */
    @Test
    void revertDiscount_restoresTotalAndHidesDiscountWhileKeepingTheTrail() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", "cortesía").andExpect(status().isNoContent());
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("97.00");

        revertDiscount(orderId, "OTHER", "se cargó por error").andExpect(status().isNoContent());

        // El total vuelve a subtotal + fees (107), no queda en 97.
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("107.00");

        // Dos filas: la aplicada sobrevive intacta y se sumó una REVERTED.
        assertThat(countDiscounts(orderId)).isEqualTo(2);
        Map<String, Object> reverted = jdbcTemplate.queryForMap(
            "SELECT * FROM order_discount WHERE order_id = ? AND action = 'REVERTED'", orderId);
        assertThat((BigDecimal) reverted.get("percentage")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) reverted.get("discount_amount")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) reverted.get("final_total_amount")).isEqualByComparingTo("107.00");
        assertThat(reverted.get("reason")).isEqualTo("OTHER");
        assertThat(reverted.get("note")).isEqualTo("se cargó por error");
        // La fila aplicada no se tocó.
        Map<String, Object> applied = jdbcTemplate.queryForMap(
            "SELECT * FROM order_discount WHERE order_id = ? AND action = 'APPLIED'", orderId);
        assertThat((BigDecimal) applied.get("percentage")).isEqualByComparingTo("10.00");

        // El detalle vuelve a no tener descuento, aunque HAY filas en la tabla.
        mockMvc.perform(get("/backoffice/orders/" + orderId)
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").doesNotExist())
            .andExpect(jsonPath("$.totalAmount").value(107.00));
    }

    /**
     * El caso que el enunciado marca como distinto de "no hay ninguna fila": la lista
     * activa debe ocultar el descuento cuando el vigente es REVERTED, no sólo cuando
     * no existe fila alguna. Cubre la ruta batch (findByOrderIdInOrderByAppliedAtDesc).
     */
    @Test
    void activeList_afterRevert_hidesDiscountEvenThoughRowsExist() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());
        assertThat(countDiscounts(orderId)).isEqualTo(2);

        mockMvc.perform(get("/backoffice/orders")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(orderId.toString()))
            .andExpect(jsonPath("$[0].discount").doesNotExist())
            .andExpect(jsonPath("$[0].totalAmount").value(107.00));
    }

    /** Reaplicar tras revertir vuelve a mostrar descuento: gana la fila más reciente. */
    @Test
    void reapplyAfterRevert_showsDiscountAgain() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());
        applyDiscount(orderId, "20", "TRANSFER_ADJUSTMENT", null).andExpect(status().isNoContent());

        assertThat(countDiscounts(orderId)).isEqualTo(3);
        mockMvc.perform(get("/backoffice/orders/" + orderId)
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount.percentage").value(20.00))
            .andExpect(jsonPath("$.totalAmount").value(87.00));
    }

    /** El resumen de turno factura el total restaurado tras revertir, no el descontado. */
    @Test
    void shiftSummary_afterRevert_billsTheRestoredTotal() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // 107 (restaurado), no 97 (el descuento revertido no se factura).
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("107.00");
    }

    @Test
    void revertDiscount_withoutCurrentDiscount_returns422() throws Exception {
        UUID orderId = createCashOrder();

        revertDiscount(orderId, "OTHER", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "El pedido no tiene un descuento vigente para revertir"));

        assertThat(countDiscounts(orderId)).isZero();
    }

    @Test
    void revertDiscount_twice_secondReturns422BecauseCurrentIsAlreadyReverted() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());

        // El vigente ya es REVERTED: no hay descuento real que revertir de nuevo.
        revertDiscount(orderId, "OTHER", null)
            .andExpect(status().isUnprocessableEntity());

        assertThat(countDiscounts(orderId)).isEqualTo(2);
    }

    @Test
    void revertDiscount_missingReason_returns400() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/discount/revert")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        // Sigue habiendo un solo descuento (el aplicado): el 400 no escribió nada.
        assertThat(countDiscounts(orderId)).isEqualTo(1);
    }

    @Test
    void revertDiscount_asStaff_returns403() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());

        mockMvc.perform(post("/backoffice/orders/" + orderId + "/discount/revert")
                .header("Authorization", "Bearer " + tokenFor(STAFF_USER_ID, "STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\"}"))
            .andExpect(status().isForbidden());

        assertThat(countDiscounts(orderId)).isEqualTo(1);
    }

    @Test
    void revertDiscount_onDeliveredOrder_returns422() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(orderId);

        // Entregado: fuera de la ventana. El descuento ya se facturó en el resumen.
        revertDiscount(orderId, "OTHER", null)
            .andExpect(status().isUnprocessableEntity());

        assertThat(countDiscounts(orderId)).isEqualTo(1);
    }

    // ── Guards de negocio contra la base real ───────────────────────────────────

    @Test
    void applyDiscount_onMercadoPagoApprovedOrder_returns422AndWritesNothing() throws Exception {
        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-discount-001");

        UUID orderId = createMercadoPagoOrder();
        initiatePayment(orderId);
        triggerApprovedWebhook("mp-discount-001", orderId);

        // MercadoPago APPROVED = ya cobrado: mensaje de "ya cobrado", no el de gateway.
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "No se puede modificar el descuento de un pedido ya cobrado"));

        assertThat(countDiscounts(orderId)).isZero();
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("107.00");
    }

    // ── US-19-07: pedido cobrado en efectivo (marcado como pagado a mano) ─────────

    /**
     * El bug reportado, end-to-end: se marca el pedido como pagado en efectivo
     * (confirmCashPayment -> Payment CASH APPROVED) y a partir de ahí no se puede ni
     * aplicar, ni modificar, ni revertir el descuento. Antes el guard sólo miraba
     * pagos de gateway, así que un CASH APPROVED se colaba.
     */
    @Test
    void cashApproved_rejectsApplyModifyAndRevert() throws Exception {
        UUID orderId = createCashOrder();

        // Descuento aplicado ANTES de cobrar (el flujo válido), y confirmado el cobro.
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        confirmCashPayment(orderId);
        assertThat(paymentStatusOf(orderId)).isEqualTo("APPROVED");

        String alreadyCharged = "No se puede modificar el descuento de un pedido ya cobrado";

        // Aplicar de nuevo (modificar) → 422 ya cobrado.
        applyDiscount(orderId, "20", "CUSTOMER_PROMO", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(alreadyCharged));

        // Revertir → 422 ya cobrado.
        revertDiscount(orderId, "OTHER", null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(alreadyCharged));

        // Nada se escribió tras el cobro: sigue el único descuento del 10%.
        assertThat(countDiscounts(orderId)).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("97.00");
    }

    /** Sin marcar el cobro, un efectivo PENDING sigue admitiendo descuento (no se rompe). */
    @Test
    void cashPending_stillAllowsDiscount() throws Exception {
        UUID orderId = createCashOrder();
        assertThat(paymentStatusOf(orderId)).isEqualTo("PENDING");

        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());

        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount())
            .isEqualByComparingTo("97.00");
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

        // US-20-02: agregados de descuento del turno. 10% de 100 = 10 descontado, en 1
        // pedido, imputado al motivo CUSTOMER_PROMO.
        assertThat((BigDecimal) summary.get("total_discount")).isEqualByComparingTo("10.00");
        assertThat(summary.get("discounted_orders")).isEqualTo(1);
        assertThat((BigDecimal) summary.get("discount_customer_promo")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) summary.get("discount_transfer_adjustment")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) summary.get("discount_other")).isEqualByComparingTo("0.00");
    }

    // ── US-20-02: agregados de descuento del turno ───────────────────────────────

    /** Varios pedidos con motivos distintos: el desglose por motivo y el total cuadran. */
    @Test
    void shiftSummary_discountBreakdownByReason() throws Exception {
        UUID promo = createCashOrder(); // 10% de 100 = 10 → CUSTOMER_PROMO
        applyDiscount(promo, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(promo);

        UUID transfer = createCashOrder(); // 20% de 100 = 20 → TRANSFER_ADJUSTMENT
        applyDiscount(transfer, "20", "TRANSFER_ADJUSTMENT", null).andExpect(status().isNoContent());
        deliver(transfer);

        UUID plain = createCashOrder(); // sin descuento
        deliver(plain);

        Map<String, Object> summary = closeShiftAndReadSummary();

        assertThat((BigDecimal) summary.get("total_discount")).isEqualByComparingTo("30.00");
        assertThat(summary.get("discounted_orders")).isEqualTo(2); // sólo los dos con descuento
        assertThat((BigDecimal) summary.get("discount_customer_promo")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) summary.get("discount_transfer_adjustment")).isEqualByComparingTo("20.00");
        assertThat((BigDecimal) summary.get("discount_other")).isEqualByComparingTo("0.00");
    }

    /** Un descuento revertido no cuenta: el pedido se facturó a su total completo. */
    @Test
    void shiftSummary_revertedDiscountIsNotCounted() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        // Hay filas en order_discount (aplicado + revertido), pero el vigente es REVERTED.
        assertThat(countDiscounts(orderId)).isEqualTo(2);
        assertThat((BigDecimal) summary.get("total_discount")).isEqualByComparingTo("0.00");
        assertThat(summary.get("discounted_orders")).isEqualTo(0);
        assertThat((BigDecimal) summary.get("total_revenue")).isEqualByComparingTo("107.00");
    }

    /** Sin descuentos, los agregados quedan en cero (la sección del PDF se ocultará). */
    @Test
    void shiftSummary_withoutDiscounts_aggregatesAreZero() throws Exception {
        UUID orderId = createCashOrder();
        deliver(orderId);

        Map<String, Object> summary = closeShiftAndReadSummary();

        assertThat((BigDecimal) summary.get("total_discount")).isEqualByComparingTo("0.00");
        assertThat(summary.get("discounted_orders")).isEqualTo(0);
    }

    /** El agregado también viaja por la API (no sólo en la columna persistida). */
    @Test
    void closeShiftResponse_exposesDiscountAggregates() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(orderId);
        jdbcTemplate.update("UPDATE branch SET accepting_orders = false WHERE id = ?", BRANCH_ID);

        mockMvc.perform(post("/backoffice/shifts/close")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDiscount").value(10.00))
            .andExpect(jsonPath("$.discountedOrders").value(1))
            .andExpect(jsonPath("$.discountCustomerPromo").value(10.00));
    }

    // ── US-20-03: detalle de pedidos del turno (on-demand) ───────────────────────

    /**
     * El detalle trae los pedidos terminales del turno —entregados Y cancelados—, con
     * su método, total, estado y el descuento vigente (con motivo). Un cancelado se
     * distingue por su estado y no aporta descuento.
     */
    @Test
    void shiftOrderDetails_includesDeliveredAndCancelled_withDiscountAndMethod() throws Exception {
        UUID delivered = createCashOrder();
        applyDiscount(delivered, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        deliver(delivered); // total 97, CASH, APPLIED CUSTOMER_PROMO

        UUID cancelled = createCashOrder();
        cancel(cancelled); // CANCELLED, sin descuento

        mockMvc.perform(get("/backoffice/shifts/" + currentShiftId() + "/order-details")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            // Fila entregada: método, total, estado y descuento con motivo.
            .andExpect(jsonPath("$[?(@.status=='DELIVERED')].paymentMethod",
                contains("CASH")))
            .andExpect(jsonPath("$[?(@.status=='DELIVERED')].totalAmount",
                contains(97.00)))
            .andExpect(jsonPath("$[?(@.status=='DELIVERED')].origin",
                contains("CLIENT")))
            .andExpect(jsonPath("$[?(@.status=='DELIVERED')].discountAmount",
                contains(10.00)))
            .andExpect(jsonPath("$[?(@.status=='DELIVERED')].discountReason",
                contains("CUSTOMER_PROMO")))
            // Fila cancelada: presente y sin descuento.
            .andExpect(jsonPath("$[?(@.status=='CANCELLED')].discountReason",
                contains(nullValue())));
    }

    /** Un descuento revertido no figura como descuento en el detalle (US-19-06). */
    @Test
    void shiftOrderDetails_revertedDiscount_showsNoDiscount() throws Exception {
        UUID orderId = createCashOrder();
        applyDiscount(orderId, "10", "CUSTOMER_PROMO", null).andExpect(status().isNoContent());
        revertDiscount(orderId, "OTHER", null).andExpect(status().isNoContent());
        deliver(orderId);

        mockMvc.perform(get("/backoffice/shifts/" + currentShiftId() + "/order-details")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("DELIVERED"))
            .andExpect(jsonPath("$[0].totalAmount").value(107.00))
            .andExpect(jsonPath("$[0].discountAmount").value(nullValue()))
            .andExpect(jsonPath("$[0].discountReason").value(nullValue()));
    }

    /** Ordenado por hora: los createdAt de las filas vienen en orden ascendente. */
    @Test
    void shiftOrderDetails_areOrderedByTime() throws Exception {
        UUID first = createCashOrder();
        deliver(first);
        UUID second = createCashOrder();
        deliver(second);
        UUID third = createCashOrder();
        cancel(third);

        String json = mockMvc.perform(get("/backoffice/shifts/" + currentShiftId() + "/order-details")
                .header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andReturn().getResponse().getContentAsString();

        var arr = objectMapper.readTree(json);
        String t0 = arr.get(0).get("createdAt").asText();
        String t1 = arr.get(1).get("createdAt").asText();
        String t2 = arr.get(2).get("createdAt").asText();
        // ISO-8601 lexicográfico == cronológico.
        assertThat(t0.compareTo(t1)).isLessThanOrEqualTo(0);
        assertThat(t1.compareTo(t2)).isLessThanOrEqualTo(0);
    }

    @Test
    void shiftOrderDetails_asStaff_returns403() throws Exception {
        UUID shiftId = currentShiftId();
        mockMvc.perform(get("/backoffice/shifts/" + shiftId + "/order-details")
                .header("Authorization", "Bearer " + tokenFor(STAFF_USER_ID, "STAFF")))
            .andExpect(status().isForbidden());
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

    private UUID currentShiftId() {
        return UUID.fromString(jdbcTemplate.queryForObject(
            "SELECT id FROM work_shift WHERE branch_id = ? AND status = 'OPEN'",
            String.class, BRANCH_ID));
    }

    /** Cancela un pedido activo (RECEIVED) desde el backoffice, con motivo. */
    private void cancel(UUID orderId) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"CANCELLED\",\"reason\":\"Prueba\"}"))
            .andExpect(status().isNoContent());
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

    private org.springframework.test.web.servlet.ResultActions revertDiscount(
            UUID orderId, String reason, String note) throws Exception {
        String body = note == null
            ? String.format("{\"reason\":\"%s\"}", reason)
            : String.format("{\"reason\":\"%s\",\"note\":\"%s\"}", reason, note);

        return mockMvc.perform(post("/backoffice/orders/" + orderId + "/discount/revert")
            .header("Authorization", "Bearer " + managerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private void insertDiscountBySql(UUID orderId, BigDecimal percentage, int appliedBy) {
        // action='APPLIED' explícito: sin él la fila violaría el NOT NULL de action y
        // el test pasaría por la razón equivocada, sin llegar a ejercer el CHECK/FK.
        jdbcTemplate.update("""
            INSERT INTO order_discount
                (id, order_id, action, percentage, original_total_amount, discount_amount,
                 final_total_amount, reason, applied_by, applied_at)
            VALUES (?, ?, 'APPLIED', ?, ?, ?, ?, ?, ?, NOW())
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

    private String paymentStatusOf(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM payment WHERE order_id = ?", String.class, orderId);
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
