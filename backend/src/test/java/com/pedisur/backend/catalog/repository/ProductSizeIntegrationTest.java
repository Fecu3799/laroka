package com.pedisur.backend.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.entity.ProductSize;
import com.pedisur.backend.catalog.entity.ProductSizeName;

/**
 * US-SIZE-01: verifica el mapeo de ProductSize contra el esquema real (V35). Con
 * ddl-auto=validate, cualquier desalineación entidad ↔ tabla rompe el arranque del
 * contexto, así que este test también es el chequeo de la migración.
 *
 * El atributo se llama `size` y en HQL `size` es también la función de tamaño de
 * colección: findByProductIdAndSize existe justamente para cubrir que la query derivada
 * resuelve el atributo y no la función.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSizeIntegrationTest {

	private static final int TENANT_ID = 1;

	@Autowired ProductSizeRepository productSizeRepository;
	@Autowired ProductRepository productRepository;
	@Autowired JdbcTemplate jdbcTemplate;

	private Integer pizzaId;
	private Integer napolitanaId;

	@BeforeAll
	void seed() {
		jdbcTemplate.execute(
			"TRUNCATE TABLE product_size, branch_product, product, category, category_type, "
				+ "staff_user, branch, tenant RESTART IDENTITY CASCADE");
		jdbcTemplate.update("INSERT INTO tenant (name, email_domain) VALUES (?, ?)", "Test Tenant", "laroka.com");
		jdbcTemplate.update(
			"INSERT INTO category_type (name, allows_half_and_half, allows_sizes, active) VALUES (?, ?, ?, ?)",
			"Pizza", true, true, true);
		jdbcTemplate.update("INSERT INTO category (name, tenant_id, category_type_id) VALUES (?, ?, ?)",
			"Pizzas", TENANT_ID, 1);
		jdbcTemplate.update(
			"INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Muzzarella", new BigDecimal("10000.00"), 1, TENANT_ID);
		jdbcTemplate.update(
			"INSERT INTO product (name, price, category_id, tenant_id) VALUES (?, ?, ?, ?)",
			"Napolitana", new BigDecimal("12000.00"), 1, TENANT_ID);
		pizzaId = 1;
		napolitanaId = 2;
	}

	@Test
	void persistsAndReadsBackSizeWithItsOwnPrice() {
		ProductSize chica = productSizeRepository.save(ProductSize.builder()
			.product(reference(pizzaId))
			.size(ProductSizeName.CHICA)
			.price(new BigDecimal("7000.00"))
			.build());

		Optional<ProductSize> found = productSizeRepository.findByProductIdAndSize(pizzaId, ProductSizeName.CHICA);

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(chica.getId());
		assertThat(found.get().getPrice()).isEqualByComparingTo("7000.00");
		// active tiene default true tanto en el builder como en la columna.
		assertThat(found.get().getActive()).isTrue();
	}

	@Test
	void findByProductIdAndActiveTrueExcludesInactiveSizes() {
		productSizeRepository.save(ProductSize.builder()
			.product(reference(napolitanaId))
			.size(ProductSizeName.GRANDE)
			.price(new BigDecimal("12000.00"))
			.build());
		productSizeRepository.save(ProductSize.builder()
			.product(reference(napolitanaId))
			.size(ProductSizeName.CHICA)
			.price(new BigDecimal("8000.00"))
			.active(false)
			.build());

		List<ProductSize> active = productSizeRepository.findByProductIdAndActiveTrue(napolitanaId);

		assertThat(active).extracting(ProductSize::getSize).containsExactly(ProductSizeName.GRANDE);
	}

	@Test
	void rejectsDuplicateSizeForSameProduct() {
		productSizeRepository.saveAndFlush(ProductSize.builder()
			.product(reference(pizzaId))
			.size(ProductSizeName.GRANDE)
			.price(new BigDecimal("15000.00"))
			.build());

		assertThatThrownBy(() -> productSizeRepository.saveAndFlush(ProductSize.builder()
			.product(reference(pizzaId))
			.size(ProductSizeName.GRANDE)
			.price(new BigDecimal("16000.00"))
			.build()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Product reference(Integer productId) {
		return productRepository.findById(productId).orElseThrow();
	}
}
