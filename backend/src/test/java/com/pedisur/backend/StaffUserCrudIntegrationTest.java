package com.pedisur.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-TEST-03 — Test de integración con DB real (Postgres) del CRUD de staff users,
 * que incluye lógica de negocio no trivial: generación de email a partir del nombre,
 * deduplicación con sufijo numérico, regeneración al cambiar el nombre, desactivación
 * lógica (soft delete) e invalidación del cache Caffeine "staffUserActive".
 *
 * Postgres real vía @ActiveProfiles("test") (servicio postgres-test, puerto 5433).
 * Cada test parte de una base limpia (@BeforeEach TRUNCATE ... RESTART IDENTITY) y del
 * cache "staffUserActive" vaciado, para no arrastrar estado entre métodos (el cache es
 * un singleton del contexto y los ids se reutilizan por RESTART IDENTITY).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffUserCrudIntegrationTest {

    private static final String JWT_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
    private static final int TENANT_ID = 1;
    private static final int BRANCH_ID = 1;
    private static final int ADMIN_USER_ID = 1;
    private static final int MANAGER_USER_ID = 2;
    private static final String EMAIL_DOMAIN = "laroka.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void seed() {
        cacheManager.getCache("staffUserActive").clear();

        jdbcTemplate.execute(
            "TRUNCATE TABLE branch_product, product, category, staff_user, branch, tenant RESTART IDENTITY CASCADE");

        jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", EMAIL_DOMAIN);
        jdbcTemplate.update("INSERT INTO branch (name, address, tenant_id) VALUES (?, ?, ?)",
            "Test Branch", "Test Address", TENANT_ID);
        // ADMIN (id=1) que ejecuta las operaciones de backoffice.
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Admin", "admin@" + EMAIL_DOMAIN, "noop", "ADMIN", BRANCH_ID);
        // MANAGER (id=2), víctima del test de desactivación con JWT vigente.
        jdbcTemplate.update(
            "INSERT INTO staff_user (name, email, password_hash, role, branch_id) VALUES (?, ?, ?, ?, ?)",
            "Manager", "manager@" + EMAIL_DOMAIN, "noop", "MANAGER", BRANCH_ID);
    }

    // ── POST genera el email a partir del nombre ──────────────────────────────────

    @Test
    void createStaffUser_generatesEmailFromName() throws Exception {
        mockMvc.perform(post("/backoffice/staff-users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Juan Pérez", "STAFF")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Juan Pérez"))
            // Acentos y espacios normalizados: "Juan Pérez" → juan.perez@laroka.com
            .andExpect(jsonPath("$.email").value("juan.perez@" + EMAIL_DOMAIN))
            .andExpect(jsonPath("$.active").value(true));
    }

    // ── Segundo usuario con nombre similar → email con sufijo numérico ────────────

    @Test
    void createSecondUserWithSimilarName_generatesEmailWithNumericSuffix() throws Exception {
        mockMvc.perform(post("/backoffice/staff-users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Juan Pérez", "STAFF")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("juan.perez@" + EMAIL_DOMAIN));

        // Mismo nombre normalizado: el base ya existe → sufijo 2.
        mockMvc.perform(post("/backoffice/staff-users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Juan Perez", "STAFF")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("juan.perez2@" + EMAIL_DOMAIN));
    }

    // ── PATCH regenera el email si cambia el nombre ───────────────────────────────

    @Test
    void updateStaffUser_regeneratesEmailWhenNameChanges() throws Exception {
        MvcResult created = mockMvc.perform(post("/backoffice/staff-users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Ana Gomez", "STAFF")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("ana.gomez@" + EMAIL_DOMAIN))
            .andReturn();
        int userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asInt();

        String updateBody = """
            {
                "name": "Ana Lopez",
                "role": "STAFF",
                "branchId": %d
            }
            """.formatted(BRANCH_ID);

        mockMvc.perform(patch("/backoffice/staff-users/" + userId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ana Lopez"))
            .andExpect(jsonPath("$.email").value("ana.lopez@" + EMAIL_DOMAIN));
    }

    // ── PATCH /status desactiva sin eliminar físicamente ──────────────────────────

    @Test
    void setStatus_deactivate_doesNotPhysicallyRemoveRecord() throws Exception {
        MvcResult created = mockMvc.perform(post("/backoffice/staff-users")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Carlos Ruiz", "STAFF")))
            .andExpect(status().isCreated())
            .andReturn();
        int userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(patch("/backoffice/staff-users/" + userId + "/status")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isOk());

        // La fila sigue existiendo (soft delete): solo cambió el flag active.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM staff_user WHERE id = ?", Integer.class, userId);
        Boolean active = jdbcTemplate.queryForObject(
            "SELECT active FROM staff_user WHERE id = ?", Boolean.class, userId);
        assertThat(count).isEqualTo(1);
        assertThat(active).isFalse();
    }

    // ── Usuario desactivado con JWT vigente → 401 (cache "staffUserActive" invalidado) ─

    @Test
    void deactivatedUser_withValidJwt_receives401_afterCacheInvalidation() throws Exception {
        String managerToken = managerToken();

        // 1. Con el usuario activo, el JWT funciona: el filtro cachea isActive(2)=true.
        mockMvc.perform(get("/backoffice/shifts/current")
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk());

        // 2. El ADMIN lo desactiva → setStatus evita el cache (@CacheEvict key=#id).
        mockMvc.perform(patch("/backoffice/staff-users/" + MANAGER_USER_ID + "/status")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
            .andExpect(status().isOk());

        // 3. El mismo JWT (aún no expirado) ahora recibe 401: el filtro relee de la DB
        //    (cache invalidado) y ve el usuario desactivado. Sin la evicción, seguiría
        //    sirviendo el true cacheado en el paso 1 y devolvería 200 (regresión).
        mockMvc.perform(get("/backoffice/shifts/current")
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isUnauthorized());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private String createBody(String name, String role) {
        return """
            {
                "name": "%s",
                "password": "secret123",
                "role": "%s",
                "branchId": %d
            }
            """.formatted(name, role, BRANCH_ID);
    }

    private String adminToken() {
        return tokenFor(ADMIN_USER_ID, "ADMIN", null, TENANT_ID);
    }

    private String managerToken() {
        return tokenFor(MANAGER_USER_ID, "MANAGER", BRANCH_ID, null);
    }

    private String tokenFor(int userId, String role, Integer branchId, Integer tenantId) {
        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        if (branchId != null) {
            builder.claim("branchId", branchId);
        }
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.compact();
    }
}
