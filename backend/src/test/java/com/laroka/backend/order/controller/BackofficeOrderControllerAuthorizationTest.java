package com.laroka.backend.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.order.entity.DiscountReason;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.mapper.OrderMapper;
import com.laroka.backend.order.service.BackofficeOrderRow;
import com.laroka.backend.order.service.OrderService;
import com.laroka.backend.payment.entity.Payment;
import com.laroka.backend.payment.service.PaymentService;
import com.laroka.backend.shared.security.JwtAuthenticationFilter;
import com.laroka.backend.shared.security.JwtService;
import com.laroka.backend.shared.security.SecurityConfig;
import com.laroka.backend.shared.security.SecurityUtils;
import com.laroka.backend.shared.security.TokenBlacklist;
import com.laroka.backend.staffuser.service.StaffUserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-17-05: el reintento de reembolso es exclusivo ADMIN. Ejercita la pila de
 * seguridad real (SecurityConfig + JwtAuthenticationFilter + @PreAuthorize) con
 * JWTs reales, siguiendo el patrón de ProductControllerAuthorizationTest.
 */
@WebMvcTest(controllers = BackofficeOrderController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
    "jwt.secret=test-secret-minimum-32-chars-for-hmac256-ok",
    "jwt.expiration=3600000",
    "cors.allowed-origins=http://localhost:5173"
})
class BackofficeOrderControllerAuthorizationTest {

    private static final String TEST_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int USER_ID = 1;
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RETRY_URL = "/backoffice/orders/" + ORDER_ID + "/retry-refund";
    private static final String DISCOUNT_URL = "/backoffice/orders/" + ORDER_ID + "/discount";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    @MockitoBean
    private StaffUserService staffUserService;

    @BeforeEach
    void setUp() {
        // El JwtAuthenticationFilter valida que el usuario del token siga activo.
        when(staffUserService.isActive(USER_ID)).thenReturn(Optional.of(true));
    }

    private String tokenWithRole(String role) {
        return Jwts.builder()
                .subject(String.valueOf(USER_ID))
                .claim("role", role)
                .claim("branchId", 1)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // --- STAFF no puede reintentar reembolsos → 403 ---

    @Test
    void staffToken_retryRefund_returns403() throws Exception {
        mockMvc.perform(post(RETRY_URL)
                .header("Authorization", "Bearer " + tokenWithRole("STAFF")))
            .andExpect(status().isForbidden());

        verify(orderService, never()).retryRefund(any(), any());
    }

    // --- MANAGER tampoco (exclusivo ADMIN) → 403 ---

    @Test
    void managerToken_retryRefund_returns403() throws Exception {
        mockMvc.perform(post(RETRY_URL)
                .header("Authorization", "Bearer " + tokenWithRole("MANAGER")))
            .andExpect(status().isForbidden());

        verify(orderService, never()).retryRefund(any(), any());
    }

    // --- ADMIN sí puede → 204 y se invoca el servicio ---

    @Test
    void adminToken_retryRefund_returns204() throws Exception {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        when(orderService.findOrderRowById(ORDER_ID))
                .thenReturn(new BackofficeOrderRow(Order.builder().build(), Payment.builder().build(), null));

        mockMvc.perform(post(RETRY_URL)
                .header("Authorization", "Bearer " + tokenWithRole("ADMIN")))
            .andExpect(status().isNoContent());

        verify(orderService).retryRefund(eq(ORDER_ID), eq(1));
    }

    // --- US-19-01: el descuento manual es de MANAGER y ADMIN, nunca de STAFF ---

    private static final String DISCOUNT_BODY =
            "{\"percentage\":10,\"reason\":\"CUSTOMER_PROMO\",\"note\":\"cortesía\"}";

    @Test
    void staffToken_applyDiscount_returns403() throws Exception {
        mockMvc.perform(post(DISCOUNT_URL)
                .header("Authorization", "Bearer " + tokenWithRole("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(DISCOUNT_BODY))
            .andExpect(status().isForbidden());

        verify(orderService, never()).applyDiscount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void managerToken_applyDiscount_returns204() throws Exception {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        when(orderService.findOrderRowById(ORDER_ID))
                .thenReturn(new BackofficeOrderRow(Order.builder().build(), Payment.builder().build(), null));

        mockMvc.perform(post(DISCOUNT_URL)
                .header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(DISCOUNT_BODY))
            .andExpect(status().isNoContent());

        verify(orderService).applyDiscount(eq(ORDER_ID), eq(1), eq(new BigDecimal("10")),
                eq(DiscountReason.CUSTOMER_PROMO), eq("cortesía"), eq(USER_ID));
    }

    @Test
    void adminToken_applyDiscount_returns204() throws Exception {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        when(orderService.findOrderRowById(ORDER_ID))
                .thenReturn(new BackofficeOrderRow(Order.builder().build(), Payment.builder().build(), null));

        mockMvc.perform(post(DISCOUNT_URL)
                .header("Authorization", "Bearer " + tokenWithRole("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(DISCOUNT_BODY))
            .andExpect(status().isNoContent());

        verify(orderService).applyDiscount(eq(ORDER_ID), eq(1), any(), any(), any(), eq(USER_ID));
    }

    // El rango 0..100 lo corta @Valid antes de llegar al service (400, no 422).
    @Test
    void managerToken_applyDiscountOutOfRange_returns400() throws Exception {
        mockMvc.perform(post(DISCOUNT_URL)
                .header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percentage\":150,\"reason\":\"CUSTOMER_PROMO\"}"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).applyDiscount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void managerToken_applyDiscountWithoutReason_returns400() throws Exception {
        mockMvc.perform(post(DISCOUNT_URL)
                .header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percentage\":10}"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).applyDiscount(any(), any(), any(), any(), any(), any());
    }
}
