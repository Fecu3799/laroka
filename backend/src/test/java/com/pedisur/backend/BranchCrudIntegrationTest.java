package com.pedisur.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * US-TEST-02 — Test de integración con DB real (Postgres) del CRUD completo de
 * sucursales, incluyendo lo agregado en sprints posteriores: fees + imagen en la
 * creación, generación automática de catálogo (BranchProduct por producto del
 * tenant), patch parcial de config, soft delete por status y scoping por tenant.
 *
 * Postgres real vía @ActiveProfiles("test") (servicio postgres-test, puerto 5433).
 * Cada test parte de una base limpia (@BeforeEach TRUNCATE ... RESTART IDENTITY)
 * para ser independiente del orden de ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BranchCrudIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int TENANT_ID = 1;
    private static final int OTHER_TENANT_ID = 2;
    private static final int ADMIN_USER_ID = 1;
    private static final int BASE_BRANCH_ID = 1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");

        // Dos tenants: el propio (1) y otro (2) para el caso de acceso cruzado.
        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Other Tenant", "other.com");

        // Sucursal base (id=1) con valores conocidos para verificar el patch parcial.
        jdbcTemplate.update(
            "INSERT INTO branch (name, address, tenant_id, delivery_fee, service_fee, phone, estimated_delivery_minutes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            "Base Branch", "Base Address", TENANT_ID,
            new BigDecimal("500.00"), new BigDecimal("200.00"), "1111", 30);

        // ADMIN activo cuyo id coincide con el subject del token.
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Admin", "admin@test.com", "noop", "ADMIN", BASE_BRANCH_ID);

        // Dos productos del tenant 1: la creación de una sucursal debe generar un
        // BranchProduct por cada uno.
        jdbcTemplate.update("INSERT INTO category (name, tenant_id) VALUES (?, ?)", "Pizzas", TENANT_ID);
        jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Muzza", new BigDecimal("10.00"), 1, TENANT_ID);
        jdbcTemplate.update("INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
            "Napolitana", new BigDecimal("12.00"), 1, TENANT_ID);
    }

    // ── POST crea sucursal con fees + imagen y genera BranchProduct por producto ──

    @Test
    void createBranch_returns201WithFeesAndImage_andGeneratesBranchProductPerTenantProduct() throws Exception {
        String body = """
            {
                "name": "Nueva Sucursal",
                "address": "Av. Siempreviva 742",
                "tenantId": %d,
                "deliveryFee": 350.50,
                "serviceFee": 120.00,
                "estimatedDeliveryMinutes": 45,
                "phone": "2804112233",
                "imageUrl": "https://cdn.test/branch.png"
            }
            """.formatted(TENANT_ID);

        MvcResult result = mockMvc.perform(post("/backoffice/branches")
                .header("Authorization", "Bearer " + adminToken(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Nueva Sucursal"))
            .andExpect(jsonPath("$.deliveryFee").value(350.50))
            .andExpect(jsonPath("$.serviceFee").value(120.00))
            .andExpect(jsonPath("$.imageUrl").value("https://cdn.test/branch.png"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

        int newBranchId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();

        // Se generó exactamente un BranchProduct (disponible) por cada producto del tenant.
        Integer generated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM branch_product WHERE branch_id = ?", Integer.class, newBranchId);
        Integer available = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM branch_product WHERE branch_id = ? AND available = true", Integer.class, newBranchId);
        assertThat(generated).isEqualTo(2);
        assertThat(available).isEqualTo(2);
    }

    // ── GET listado no lanza LazyInitializationException (tenant lazy) ─────────────

    @Test
    void listBranches_asAdmin_returns200_withoutLazyInitializationException() throws Exception {
        // Con open-in-view=false, mapear branch.getTenant() fuera de sesión rompía con
        // LazyInitializationException (500). El @EntityGraph del repositorio lo evita:
        // el endpoint responde 200 con el tenant resuelto.
        mockMvc.perform(get("/backoffice/branches")
                .header("Authorization", "Bearer " + adminToken(TENANT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Base Branch"))
            .andExpect(jsonPath("$[0].tenantId").value(TENANT_ID))
            .andExpect(jsonPath("$[0].tenant.id").value(TENANT_ID));
    }

    // ── PATCH /config aplica patch parcial: solo pisa los campos provistos ────────

    @Test
    void updateConfig_partialPatch_updatesOnlyProvidedFields() throws Exception {
        // Solo name y serviceFee (+ el obligatorio maxShiftDurationMinutes). address,
        // phone y deliveryFee se omiten y deben conservar su valor original.
        String body = """
            {
                "maxShiftDurationMinutes": 600,
                "name": "Sucursal Renombrada",
                "serviceFee": 350.00
            }
            """;

        mockMvc.perform(patch("/backoffice/branches/" + BASE_BRANCH_ID + "/config")
                .header("Authorization", "Bearer " + adminToken(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Sucursal Renombrada"))
            .andExpect(jsonPath("$.serviceFee").value(350.00))
            .andExpect(jsonPath("$.maxShiftDurationMinutes").value(600))
            // Campos omitidos: se conservan.
            .andExpect(jsonPath("$.address").value("Base Address"))
            .andExpect(jsonPath("$.phone").value("1111"))
            .andExpect(jsonPath("$.deliveryFee").value(500.00));

        // Verificación directa en DB para descartar cualquier serialización engañosa.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT name FROM branch WHERE id = ?", String.class, BASE_BRANCH_ID))
            .isEqualTo("Sucursal Renombrada");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT service_fee FROM branch WHERE id = ?", BigDecimal.class, BASE_BRANCH_ID))
            .isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT delivery_fee FROM branch WHERE id = ?", BigDecimal.class, BASE_BRANCH_ID))
            .isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT phone FROM branch WHERE id = ?", String.class, BASE_BRANCH_ID))
            .isEqualTo("1111");
    }

    // ── PATCH /status desactiva sin eliminar físicamente ──────────────────────────

    @Test
    void setStatus_deactivate_softDeletesWithoutPhysicalRemoval() throws Exception {
        mockMvc.perform(patch("/backoffice/branches/" + BASE_BRANCH_ID + "/status")
                .header("Authorization", "Bearer " + adminToken(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isOk());

        // La fila sigue existiendo (soft delete): solo cambió el flag active.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM branch WHERE id = ?", Integer.class, BASE_BRANCH_ID);
        Boolean active = jdbcTemplate.queryForObject(
            "SELECT active FROM branch WHERE id = ?", Boolean.class, BASE_BRANCH_ID);
        assertThat(count).isEqualTo(1);
        assertThat(active).isFalse();
    }

    // ── PATCH /status: una sucursal con turno abierto no puede desactivarse ────────

    @Test
    void setStatus_deactivateWithOpenShift_returns400() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO work_shift (id, branch_id, opened_by, opened_at, status) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'OPEN')",
            UUID.randomUUID(), BASE_BRANCH_ID, ADMIN_USER_ID);

        mockMvc.perform(patch("/backoffice/branches/" + BASE_BRANCH_ID + "/status")
                .header("Authorization", "Bearer " + adminToken(TENANT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("No se puede desactivar una sucursal con un turno abierto"));

        // No se desactivó: sigue activa.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT active FROM branch WHERE id = ?", Boolean.class, BASE_BRANCH_ID)).isTrue();
    }

    // ── Acceso a sucursal de otro tenant → 403 en las rutas con scope de tenant ───

    @Test
    void crossTenantBranch_configAndStatus_return403() throws Exception {
        // Token ADMIN del tenant 2 operando sobre la sucursal 1 (tenant 1).
        String otherTenantToken = adminToken(OTHER_TENANT_ID);

        mockMvc.perform(patch("/backoffice/branches/" + BASE_BRANCH_ID + "/config")
                .header("Authorization", "Bearer " + otherTenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxShiftDurationMinutes\": 600}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch("/backoffice/branches/" + BASE_BRANCH_ID + "/status")
                .header("Authorization", "Bearer " + otherTenantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isForbidden());

        // La sucursal ajena quedó intacta.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT active FROM branch WHERE id = ?", Boolean.class, BASE_BRANCH_ID)).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private String adminToken(int tenantId) {
        return Jwts.builder()
            .subject(String.valueOf(ADMIN_USER_ID))
            .claim("role", "ADMIN")
            .claim("tenantId", tenantId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
