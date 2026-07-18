package com.laroka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.repository.OrderRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

/**
 * US-TEST-05 — Test de integración con DB real (Postgres) del envío de push ante
 * cambios de estado de pedido, mockeando el cliente de Web Push ({@link PushService}).
 *
 * Verifica que: (1) una transición con push_subscription_id asociado dispara el envío;
 * (2) un fallo del servicio de push no interrumpe ni revierte la transición de estado;
 * (3) un 410/404 del cliente desvincula la suscripción del pedido (push_subscription_id
 * = null) y borra la suscripción.
 *
 * El envío real es @Async fire-and-forget. Para asertar de forma determinista se fuerza
 * el executor a síncrono con un AsyncConfigurer de test (SyncAsyncConfig), de modo que
 * sendNotification corre inline dentro de la request. Se usan claves EC P-256 válidas
 * reales porque el constructor de Notification decodifica criptográficamente la clave;
 * solo el envío HTTP (PushService.send) se mockea.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PushNotificationIntegrationTest.SyncAsyncConfig.class)
class PushNotificationIntegrationTest {

    /** Fuerza @Async a ejecutarse inline para que las aserciones sean deterministas. */
    @TestConfiguration
    static class SyncAsyncConfig implements AsyncConfigurer {
        @Override
        public Executor getAsyncExecutor() {
            return new SyncTaskExecutor();
        }
    }

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int BRANCH_ID = 1;
    private static final int STAFF_USER_ID = 1;
    private static final String ENDPOINT = "https://push.example.com/sub/abc123";

    // Claves EC P-256 válidas (uncompressed point base64url + auth de 16 bytes): el
    // constructor de Notification las decodifica de verdad, así que deben ser válidas.
    private static final String P256DH;
    private static final String AUTH;
    static {
        try {
            // El PushService real registra BouncyCastle al construirse; como acá está
            // mockeado, lo registramos a mano para que Notification pueda decodificar la
            // clave EC (Utils.loadPublicKey usa KeyFactory con el provider "BC").
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            ECPublicKey pub = (ECPublicKey) kpg.generateKeyPair().getPublic();
            byte[] x = toUnsigned32(pub.getW().getAffineX());
            byte[] y = toUnsigned32(pub.getW().getAffineY());
            byte[] uncompressed = new byte[65];
            uncompressed[0] = 0x04;
            System.arraycopy(x, 0, uncompressed, 1, 32);
            System.arraycopy(y, 0, uncompressed, 33, 32);
            P256DH = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
            byte[] authBytes = new byte[16];
            new SecureRandom().nextBytes(authBytes);
            AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(authBytes);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PushService pushService;

    private UUID subscriptionId;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE push_subscription, branch_product, product, category, staff_user, branch, tenant "
                + "RESTART IDENTITY CASCADE");
        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
            "Test Branch", "Test Address", 1);
        jdbcTemplate.update("UPDATE branch SET accepting_orders = true WHERE id = ?", BRANCH_ID);
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Staff", "staff@test.com", "noop", "STAFF", 1);
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Test Category", 1);
        jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Test Pizza", new java.math.BigDecimal("10.00"), 1, 1);
        jdbcTemplate.update("INSERT INTO branch_product (branch_id, product_id) VALUES (?, ?)", BRANCH_ID, 1);
        jdbcTemplate.update(
            "INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
            UUID.randomUUID(), BRANCH_ID, STAFF_USER_ID);

        subscriptionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO push_subscription (id, endpoint, p256dh, auth) VALUES (?, ?, ?, ?)",
            subscriptionId, ENDPOINT, P256DH, AUTH);
    }

    // ── Transición con suscripción asociada → dispara el envío ────────────────────

    @Test
    void transitionWithSubscription_triggersPushSend() throws Exception {
        HttpResponse ok = httpResponse(201);
        when(pushService.send(any(Notification.class))).thenReturn(ok);

        UUID orderId = createReceivedOrderLinkedToSubscription();
        transition(orderId, "IN_PREPARATION");

        verify(pushService).send(any(Notification.class));
        // La suscripción sigue vinculada al pedido (envío OK, sin desvincular).
        assertThat(pushSubscriptionIdOf(orderId)).isEqualTo(subscriptionId.toString());
    }

    // ── Fallo del push no interrumpe ni revierte la transición ────────────────────

    @Test
    void pushFailure_doesNotRevertTransition() throws Exception {
        when(pushService.send(any(Notification.class))).thenThrow(new RuntimeException("push service down"));

        UUID orderId = createReceivedOrderLinkedToSubscription();
        // La transición responde OK pese al fallo del push (se traga en fire-and-forget).
        transition(orderId, "IN_PREPARATION");

        verify(pushService).send(any(Notification.class));
        // La transición quedó persistida: el pedido está en el nuevo estado.
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.IN_PREPARATION);
        // Un fallo genérico no desvincula la suscripción (solo 404/410 lo hacen).
        assertThat(pushSubscriptionIdOf(orderId)).isEqualTo(subscriptionId.toString());
    }

    // ── 410 del cliente de push → desvincula la suscripción del pedido ────────────

    @Test
    void gone410_unlinksSubscriptionFromOrder() throws Exception {
        HttpResponse gone = httpResponse(410);
        when(pushService.send(any(Notification.class))).thenReturn(gone);

        UUID orderId = createReceivedOrderLinkedToSubscription();
        transition(orderId, "IN_PREPARATION");

        verify(pushService).send(any(Notification.class));
        // La suscripción expirada se desvincula del pedido (push_subscription_id = null)...
        assertThat(pushSubscriptionIdOf(orderId)).isNull();
        // ...y la fila de suscripción se elimina.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM push_subscription WHERE id = ?", Integer.class, subscriptionId);
        assertThat(count).isZero();
        // La transición no se ve afectada.
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.IN_PREPARATION);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /** Crea un pedido TAKEAWAY CASH (auto RECEIVED) y lo vincula a la suscripción. */
    private UUID createReceivedOrderLinkedToSubscription() throws Exception {
        String body = """
            {
                "branchId": %d,
                "orderType": "TAKEAWAY",
                "paymentMethod": "CASH",
                "items": [{"productId": 1, "quantity": 1}]
            }
            """.formatted(BRANCH_ID);
        MvcResult result = mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        UUID orderId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText());

        jdbcTemplate.update("UPDATE orders SET push_subscription_id = ? WHERE id = ?", subscriptionId, orderId);
        return orderId;
    }

    private void transition(UUID orderId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/backoffice/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextStatus\":\"" + nextStatus + "\"}"))
            .andExpect(status().isNoContent());
    }

    private String pushSubscriptionIdOf(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT push_subscription_id FROM orders WHERE id = ?", String.class, orderId);
    }

    private HttpResponse httpResponse(int statusCode) {
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
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

    /** BigInteger → 32 bytes big-endian sin signo (coordenada de curva P-256). */
    private static byte[] toUnsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        if (bytes.length == 32) {
            return bytes;
        }
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, out, 0, 32);
        } else {
            System.arraycopy(bytes, 0, out, 32 - bytes.length, bytes.length);
        }
        return out;
    }
}
