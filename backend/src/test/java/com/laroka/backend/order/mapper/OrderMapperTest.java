package com.laroka.backend.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.order.dto.BackofficeOrderResponseDTO;
import com.laroka.backend.order.dto.CreateOrderResponseDTO;
import com.laroka.backend.order.dto.OrderDiscountDTO;
import com.laroka.backend.order.entity.DiscountReason;
import com.laroka.backend.order.entity.Order;
import com.laroka.backend.order.entity.OrderDiscount;
import com.laroka.backend.order.entity.OrderItem;
import com.laroka.backend.order.entity.OrderStatus;
import com.laroka.backend.order.entity.OrderType;
import com.laroka.backend.order.service.AppliedDiscount;
import com.laroka.backend.order.service.BackofficeOrderDetail;

/**
 * US-HH-01: los ítems exponen la segunda mitad cuando corresponde (mitad y mitad) y la
 * dejan en null en los ítems simples, sin cambiar el comportamiento existente.
 */
class OrderMapperTest {

	private final OrderMapper mapper = new OrderMapper();

	private Product product(Integer id, String name) {
		return Product.builder().id(id).name(name).build();
	}

	private OrderItem simpleItem() {
		return OrderItem.builder()
			.id(UUID.randomUUID())
			.product(product(1, "Muzzarella"))
			.quantity(1)
			.unitPrice(new BigDecimal("2800.00"))
			.subtotal(new BigDecimal("2800.00"))
			.build();
	}

	private OrderItem halfAndHalfItem() {
		return OrderItem.builder()
			.id(UUID.randomUUID())
			.product(product(1, "Muzzarella"))
			.secondProduct(product(2, "Napolitana"))
			.quantity(1)
			.unitPrice(new BigDecimal("3200.00"))
			.subtotal(new BigDecimal("3200.00"))
			.build();
	}

	private Order order() {
		return Order.builder()
			.id(UUID.randomUUID())
			.status(OrderStatus.RECEIVED)
			.orderType(OrderType.TAKEAWAY)
			.subtotal(new BigDecimal("6000.00"))
			.deliveryFee(BigDecimal.ZERO)
			.serviceFee(BigDecimal.ZERO)
			.totalAmount(new BigDecimal("6000.00"))
			.branch(Branch.builder().id(1).name("Centro").build())
			.items(List.of(simpleItem(), halfAndHalfItem()))
			.build();
	}

	@Test
	void toResponseDTO_exposesSecondProduct_onlyForHalfAndHalfItem() {
		CreateOrderResponseDTO dto = mapper.toResponseDTO(order());

		assertThat(dto.getItems())
			.extracting("productName", "secondProductId", "secondProductName")
			.containsExactly(
				tuple("Muzzarella", null, null),
				tuple("Muzzarella", 2, "Napolitana"));
	}

	@Test
	void toBackofficeResponseDTO_exposesSecondProductName_onlyForHalfAndHalfItem() {
		BackofficeOrderResponseDTO dto = mapper.toBackofficeResponseDTO(order(), null);

		assertThat(dto.getItems())
			.extracting("productName", "secondProductName")
			.containsExactly(
				tuple("Muzzarella", null),
				tuple("Muzzarella", "Napolitana"));
	}

	// El backoffice también expone el tamaño del ítem, con el mismo criterio: null cuando el
	// ítem no lleva tamaño, que es el caso del grande (implícito, sin fila en product_size).

	private OrderItem sizedItem() {
		return OrderItem.builder()
			.id(UUID.randomUUID())
			.product(product(1, "Muzzarella"))
			.productSize(ProductSize.builder()
				.id(50).size(ProductSizeName.CHICA).price(new BigDecimal("9000.00")).build())
			.quantity(1)
			.unitPrice(new BigDecimal("9000.00"))
			.subtotal(new BigDecimal("9000.00"))
			.build();
	}

	@Test
	void toBackofficeResponseDTO_exposesSizeName_onlyForSizedItem() {
		Order withSize = Order.builder()
			.id(UUID.randomUUID())
			.status(OrderStatus.RECEIVED)
			.orderType(OrderType.TAKEAWAY)
			.subtotal(new BigDecimal("11800.00"))
			.deliveryFee(BigDecimal.ZERO)
			.serviceFee(BigDecimal.ZERO)
			.totalAmount(new BigDecimal("11800.00"))
			.branch(Branch.builder().id(1).name("Centro").build())
			.items(List.of(simpleItem(), sizedItem()))
			.build();

		BackofficeOrderResponseDTO dto = mapper.toBackofficeResponseDTO(withSize, null);

		assertThat(dto.getItems())
			.extracting("productName", "sizeName")
			.containsExactly(
				tuple("Muzzarella", null),
				tuple("Muzzarella", "CHICA"));
	}

	@Test
	void toBackofficeResponseDTO_halfAndHalfItem_leavesSizeNameNull() {
		// Tamaño y mitad y mitad son excluyentes (US-SIZE-03): nunca coinciden en un ítem.
		BackofficeOrderResponseDTO dto = mapper.toBackofficeResponseDTO(order(), null);

		assertThat(dto.getItems()).extracting("sizeName").containsOnlyNulls();
	}

	// ── US-19-03: descuento vigente expuesto en el detalle ──────────────────────

	private OrderDiscount discount() {
		return OrderDiscount.builder()
			.id(UUID.randomUUID())
			.percentage(new BigDecimal("10.00"))
			.originalTotalAmount(new BigDecimal("6000.00"))
			.discountAmount(new BigDecimal("600.00"))
			.finalTotalAmount(new BigDecimal("5400.00"))
			.reason(DiscountReason.CUSTOMER_PROMO)
			.note("cortesía por demora")
			.appliedBy(7)
			.appliedAt(LocalDateTime.of(2026, 7, 23, 20, 30))
			.build();
	}

	@Test
	void toBackofficeDetailDTO_withDiscount_exposesAmountsAndTraceability() {
		BackofficeOrderDetail detail = new BackofficeOrderDetail(
			order(), null, List.of(), new AppliedDiscount(discount(), "Ana Gómez"));

		OrderDiscountDTO dto = mapper.toBackofficeDetailDTO(detail).getDiscount();

		assertThat(dto).isNotNull();
		assertThat(dto.getPercentage()).isEqualByComparingTo("10.00");
		assertThat(dto.getOriginalTotalAmount()).isEqualByComparingTo("6000.00");
		assertThat(dto.getDiscountAmount()).isEqualByComparingTo("600.00");
		assertThat(dto.getFinalTotalAmount()).isEqualByComparingTo("5400.00");
		assertThat(dto.getReason()).isEqualTo(DiscountReason.CUSTOMER_PROMO);
		assertThat(dto.getNote()).isEqualTo("cortesía por demora");
		assertThat(dto.getAppliedByName()).isEqualTo("Ana Gómez");
		assertThat(dto.getAppliedAt()).isEqualTo(LocalDateTime.of(2026, 7, 23, 20, 30));
	}

	@Test
	void toBackofficeDetailDTO_withoutDiscount_leavesDiscountNull() {
		BackofficeOrderDetail detail = new BackofficeOrderDetail(order(), null, List.of(), null);

		assertThat(mapper.toBackofficeDetailDTO(detail).getDiscount()).isNull();
	}

	@Test
	void toBackofficeDetailDTO_discountFromDeletedUser_keepsAmountsWithNullName() {
		// La fila del descuento sobrevive al borrado de quien lo aplicó: es traza de
		// auditoría y no debe romper el detalle.
		BackofficeOrderDetail detail = new BackofficeOrderDetail(
			order(), null, List.of(), new AppliedDiscount(discount(), null));

		OrderDiscountDTO dto = mapper.toBackofficeDetailDTO(detail).getDiscount();

		assertThat(dto.getAppliedByName()).isNull();
		assertThat(dto.getDiscountAmount()).isEqualByComparingTo("600.00");
	}
}
