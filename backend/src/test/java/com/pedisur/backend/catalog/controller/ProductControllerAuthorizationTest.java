package com.pedisur.backend.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pedisur.backend.catalog.dto.ProductResponseDTO;
import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.entity.ProductSizeName;
import com.pedisur.backend.catalog.mapper.BranchProductConfigMapper;
import com.pedisur.backend.catalog.mapper.ProductMapper;
import com.pedisur.backend.catalog.mapper.ProductSizeMapper;
import com.pedisur.backend.catalog.service.ProductService;
import com.pedisur.backend.catalog.service.ProductSizeService;
import com.pedisur.backend.shared.security.JwtAuthenticationFilter;
import com.pedisur.backend.shared.security.JwtService;
import com.pedisur.backend.shared.security.SecurityConfig;
import com.pedisur.backend.shared.security.SecurityUtils;
import com.pedisur.backend.shared.security.TokenBlacklist;
import com.pedisur.backend.staffuser.service.StaffUserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * US-14-01: control de rol en los endpoints de catálogo de productos.
 * Ejercita la pila de seguridad real (SecurityConfig + JwtAuthenticationFilter +
 * @PreAuthorize) con JWTs reales, siguiendo el patrón de SecurityIntegrationTest.
 */
@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
	"jwt.secret=test-secret-minimum-32-chars-for-hmac256-ok",
	"jwt.expiration=3600000",
	"cors.allowed-origins=http://localhost:5173"
})
class ProductControllerAuthorizationTest {

	private static final String TEST_SECRET = "test-secret-minimum-32-chars-for-hmac256-ok";
	private static final int USER_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService service;

	@MockitoBean
	private ProductMapper mapper;

	@MockitoBean
	private BranchProductConfigMapper branchProductConfigMapper;

	// US-SIZE-04: nuevas dependencias del controller.
	@MockitoBean
	private ProductSizeService productSizeService;

	@MockitoBean
	private ProductSizeMapper productSizeMapper;

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

	// --- STAFF no puede crear productos → 403 ---

	@Test
	void staffToken_createProduct_returns403() throws Exception {
		String body = "{\"name\":\"Muzza\",\"price\":1200.00,\"categoryId\":1,\"tenantId\":1}";

		mockMvc.perform(post("/backoffice/products")
				.header("Authorization", "Bearer " + tokenWithRole("STAFF"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());

		verify(service, never()).create(any());
	}

	// --- STAFF no puede eliminar productos → 403 ---

	@Test
	void staffToken_deleteProduct_returns403() throws Exception {
		mockMvc.perform(delete("/backoffice/products/1")
				.header("Authorization", "Bearer " + tokenWithRole("STAFF")))
			.andExpect(status().isForbidden());

		verify(service, never()).delete(anyInt());
	}

	// --- MANAGER puede togglear disponibilidad → 200 ---

	@Test
	void managerToken_toggleAvailability_returns200() throws Exception {
		Product product = new Product();
		when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
		when(service.updateAvailability(eq(1), eq(true), eq(1))).thenReturn(product);
		when(mapper.toResponseDTO(product)).thenReturn(new ProductResponseDTO());

		String body = "{\"available\":true}";

		mockMvc.perform(patch("/backoffice/products/1/availability")
				.header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());

		verify(service).updateAvailability(1, true, 1);
	}

	// --- Tamaños (US-SIZE-04) ---
	//
	// El precio base del tamaño es del catálogo del tenant, igual que PUT /{id}/price: sólo
	// ADMIN. El override por sucursal es operativo: MANAGER o ADMIN, mismo criterio que
	// branch-config del producto.

	@Test
	void managerToken_createSize_returns403() throws Exception {
		String body = "{\"size\":\"CHICA\",\"price\":9000.00}";

		mockMvc.perform(post("/backoffice/products/1/sizes")
				.header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());

		verify(productSizeService, never()).create(any(), any(), any());
	}

	@Test
	void staffToken_createSize_returns403() throws Exception {
		String body = "{\"size\":\"CHICA\",\"price\":9000.00}";

		mockMvc.perform(post("/backoffice/products/1/sizes")
				.header("Authorization", "Bearer " + tokenWithRole("STAFF"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());

		verify(productSizeService, never()).create(any(), any(), any());
	}

	@Test
	void managerToken_updateSize_returns403() throws Exception {
		mockMvc.perform(patch("/backoffice/products/1/sizes/10")
				.header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isForbidden());

		verify(productSizeService, never()).update(any(), any(), any(), any());
	}

	@Test
	void adminToken_createSize_returns201() throws Exception {
		when(productSizeService.create(eq(1), eq(ProductSizeName.CHICA), any()))
			.thenReturn(new ProductSize());

		mockMvc.perform(post("/backoffice/products/1/sizes")
				.header("Authorization", "Bearer " + tokenWithRole("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"size\":\"CHICA\",\"price\":9000.00}"))
			.andExpect(status().isCreated());

		verify(productSizeService).create(eq(1), eq(ProductSizeName.CHICA), any());
	}

	@Test
	void managerToken_updateSizeBranchConfig_returns204() throws Exception {
		mockMvc.perform(patch("/backoffice/products/1/sizes/10/branch-config")
				.header("Authorization", "Bearer " + tokenWithRole("MANAGER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1,\"priceOverride\":9900.00}"))
			.andExpect(status().isNoContent());

		verify(productSizeService).updateBranchOverride(eq(1), eq(1), eq(10), any());
	}

	@Test
	void staffToken_updateSizeBranchConfig_returns403() throws Exception {
		mockMvc.perform(patch("/backoffice/products/1/sizes/10/branch-config")
				.header("Authorization", "Bearer " + tokenWithRole("STAFF"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"branchId\":1,\"priceOverride\":9900.00}"))
			.andExpect(status().isForbidden());

		verify(productSizeService, never()).updateBranchOverride(any(), any(), any(), any());
	}
}
