package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.repository.OrderRepository;
import com.laroka.backend.order.repository.OrderStatusHistoryRepository;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.entity.PaymentStatus;
import com.laroka.backend.payment.gateway.PaymentGateway;
import com.laroka.backend.payment.repository.PaymentRepository;
import com.laroka.backend.order.entity.OrderStatusHistory;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFlowsIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int BRANCH_ID = 1;
    private static final int STAFF_USER_ID = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderStatusHistoryRepository historyRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PaymentGateway paymentGateway;

    @BeforeAll
    void setupShift() {
        jdbcTemplate.execute("TRUNCATE TABLE work_shift CASCADE");
        jdbcTemplate.update(
            "INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
            UUID.randomUUID(), BRANCH_ID, STAFF_USER_ID);
    }

    @BeforeEach
    void resetMocks() {
        Mockito.reset(paymentGateway);
    }

    // --- helpers ---

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

    private UUID createOrder(String orderType, String paymentMethod, String deliveryAddress) throws Exception {
        String items = "[{\"productId\":1,\"quantity\":1}]";
        String body = String.format("""
            {
                "branchId": %d,
                "orderType": "%s",
                "paymentMethod": "%s"%s,
                "items": %s
            }
            """,
            BRANCH_ID,
            orderType,
            paymentMethod,
            deliveryAddress != null ? ",\"deliveryAddress\":\"" + deliveryAddress + "\"" : "",
            items
        );

        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();

        return UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderId").asText()
        );
    }

    private void triggerWebhook(String mpPaymentId, UUID orderId) throws Exception {
        when(paymentGateway.fetchPayment(mpPaymentId))
            .thenReturn(new PaymentGateway.PaymentInfo("approved", orderId.toString()));

        String body = String.format("{\"type\":\"payment\",\"data\":{\"id\":\"%s\"}}", mpPaymentId);

        mockMvc.perform(post("/payments/webhook")
                .param("data.id", mpPaymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    private void transitionStatus(UUID orderId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"" + nextStatus + "\"}"))
            .andExpect(status().isNoContent());
    }

    private void assertHistory(UUID orderId, int expectedSize, OrderStatus expectedFromAt,
                               OrderStatus expectedToAt, int index) {
        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        assertThat(history).hasSizeGreaterThanOrEqualTo(index + 1);
        assertThat(history.get(index).getFromStatus()).isEqualTo(expectedFromAt);
        assertThat(history.get(index).getToStatus()).isEqualTo(expectedToAt);
    }

    // --- Flujo 1: DELIVERY + MercadoPago ---

    @Test
    @Order(1)
    void deliveryFlow_mercadopago_pendingPaymentToDelivered() throws Exception {
        String mpPaymentId = "mp-delivery-001";

        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-delivery-001");

        // 1. Crear pedido DELIVERY → PENDING_PAYMENT
        UUID orderId = createOrder("DELIVERY", "MERCADOPAGO", "Av. Rivadavia 1234");

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertHistory(orderId, 1, null, OrderStatus.PENDING_PAYMENT, 0);

        // 2. Iniciar pago
        mockMvc.perform(post("/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentLink").value("https://mp-test.com?pref_id=pref-delivery-001"));

        // 3. Webhook aprobado → RECEIVED
        triggerWebhook(mpPaymentId, orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        assertHistory(orderId, 2, OrderStatus.PENDING_PAYMENT, OrderStatus.RECEIVED, 1);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getMercadopagoPaymentId()).isEqualTo(mpPaymentId);

        // 4. RECEIVED → IN_PREPARATION
        transitionStatus(orderId, "IN_PREPARATION");
        assertHistory(orderId, 3, OrderStatus.RECEIVED, OrderStatus.IN_PREPARATION, 2);

        // 5. IN_PREPARATION → ON_THE_WAY
        transitionStatus(orderId, "ON_THE_WAY");
        assertHistory(orderId, 4, OrderStatus.IN_PREPARATION, OrderStatus.ON_THE_WAY, 3);

        // 6. ON_THE_WAY → DELIVERED
        transitionStatus(orderId, "DELIVERED");
        assertHistory(orderId, 5, OrderStatus.ON_THE_WAY, OrderStatus.DELIVERED, 4);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.DELIVERED);
    }

    // --- Flujo 2: TAKEAWAY + CASH ---

    @Test
    @Order(2)
    void takeawayFlow_cash_autoReceivedToDelivered() throws Exception {
        // 1. Crear pedido TAKEAWAY CASH → auto RECEIVED (con pago PENDING creado)
        UUID orderId = createOrder("TAKEAWAY", "CASH", null);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        // history: null→PENDING_PAYMENT y PENDING_PAYMENT→RECEIVED
        assertHistory(orderId, 2, OrderStatus.PENDING_PAYMENT, OrderStatus.RECEIVED, 1);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // 2. Confirmar pago en efectivo
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/payment")
                .header("Authorization", "Bearer " + staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CONFIRM\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.method").value("CASH"))
            .andExpect(jsonPath("$.paidAt").isNotEmpty());

        payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPaidAt()).isNotNull();

        // 3. RECEIVED → IN_PREPARATION
        transitionStatus(orderId, "IN_PREPARATION");
        assertHistory(orderId, 3, OrderStatus.RECEIVED, OrderStatus.IN_PREPARATION, 2);

        // 4. IN_PREPARATION → READY_FOR_PICKUP
        transitionStatus(orderId, "READY_FOR_PICKUP");
        assertHistory(orderId, 4, OrderStatus.IN_PREPARATION, OrderStatus.READY_FOR_PICKUP, 3);

        // 5. READY_FOR_PICKUP → DELIVERED
        transitionStatus(orderId, "DELIVERED");
        assertHistory(orderId, 5, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED, 4);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.DELIVERED);
    }

    // --- Flujo 3: Pedido manual backoffice + QR MercadoPago ---

    @Test
    @Order(3)
    void takeawayFlow_mercadopagoQr_pendingPaymentToDelivered() throws Exception {
        String mpPaymentId = "mp-qr-001";

        when(paymentGateway.createPreference(any(), any(), any()))
            .thenReturn("https://mp-test.com?pref_id=pref-qr-001");

        // 1. Crear pedido TAKEAWAY MERCADOPAGO → PENDING_PAYMENT
        UUID orderId = createOrder("TAKEAWAY", "MERCADOPAGO", null);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertHistory(orderId, 1, null, OrderStatus.PENDING_PAYMENT, 0);

        // 2. Iniciar pago QR
        mockMvc.perform(post("/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentLink").value("https://mp-test.com?pref_id=pref-qr-001"));

        // 3. Webhook aprobado (cliente escanea QR) → RECEIVED
        triggerWebhook(mpPaymentId, orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.RECEIVED);
        assertHistory(orderId, 2, OrderStatus.PENDING_PAYMENT, OrderStatus.RECEIVED, 1);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPaidAt()).isNotNull();

        // 4. RECEIVED → IN_PREPARATION
        transitionStatus(orderId, "IN_PREPARATION");
        assertHistory(orderId, 3, OrderStatus.RECEIVED, OrderStatus.IN_PREPARATION, 2);

        // 5. IN_PREPARATION → READY_FOR_PICKUP
        transitionStatus(orderId, "READY_FOR_PICKUP");
        assertHistory(orderId, 4, OrderStatus.IN_PREPARATION, OrderStatus.READY_FOR_PICKUP, 3);

        // 6. READY_FOR_PICKUP → DELIVERED
        transitionStatus(orderId, "DELIVERED");
        assertHistory(orderId, 5, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED, 4);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.DELIVERED);
    }
}
