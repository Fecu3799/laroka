package com.laroka.backend.catalog.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.exception.BranchNotFoundException;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.catalog.entity.BranchProductSize;
import com.laroka.backend.catalog.entity.Category;
import com.laroka.backend.catalog.entity.CategoryType;
import com.laroka.backend.catalog.entity.Product;
import com.laroka.backend.catalog.entity.ProductSize;
import com.laroka.backend.catalog.entity.ProductSizeName;
import com.laroka.backend.catalog.exception.ProductNotFoundException;
import com.laroka.backend.catalog.exception.ProductSizeNotFoundException;
import com.laroka.backend.catalog.exception.UnsupportedProductSizeException;
import com.laroka.backend.catalog.repository.BranchProductSizeRepository;
import com.laroka.backend.catalog.repository.ProductRepository;
import com.laroka.backend.catalog.repository.ProductSizeRepository;
import com.laroka.backend.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSizeService {

	private final BranchProductSizeRepository branchProductSizeRepository;
	private final ProductSizeRepository productSizeRepository;
	private final ProductRepository productRepository;
	private final BranchRepository branchRepository;

	/**
	 * US-SIZE-02: precio efectivo de un tamaño en una sucursal —
	 * {@code branch_product_size.price_override ?? product_size.price}.
	 *
	 * Mismo criterio que el precio efectivo del producto sin tamaño
	 * (OrderService.effectivePrice): sin fila de override, o con la fila pero
	 * price_override en NULL, vale el precio base del tamaño.
	 */
	public BigDecimal resolveEffectivePrice(Integer branchId, ProductSize productSize) {
		BigDecimal override = branchProductSizeRepository
			.findByBranchIdAndProductSizeId(branchId, productSize.getId())
			.map(BranchProductSize::getPriceOverride)
			.orElse(null);
		return override != null ? override : productSize.getPrice();
	}

	/**
	 * US-SIZE-F-02: tamaños activos de la sucursal, agrupados por producto y con el precio ya
	 * resuelto por la fórmula de US-SIZE-02. Dos queries en total (tamaños + overrides),
	 * independientemente de cuántos productos tenga el menú.
	 *
	 * Los productos sin tamaños activos simplemente no aparecen en el mapa.
	 */
	public Map<Integer, List<ResolvedProductSize>> resolveSizesForBranch(Integer branchId) {
		List<ProductSize> sizes = productSizeRepository.findActiveByBranchId(branchId);
		if (sizes.isEmpty()) {
			return Map.of();
		}

		Map<Integer, BigDecimal> overrides = branchProductSizeRepository.findByBranchId(branchId).stream()
			.filter(bps -> bps.getPriceOverride() != null)
			.collect(Collectors.toMap(bps -> bps.getProductSize().getId(),
				BranchProductSize::getPriceOverride));

		return sizes.stream().collect(Collectors.groupingBy(
			ps -> ps.getProduct().getId(),
			Collectors.mapping(
				ps -> new ResolvedProductSize(ps.getId(), ps.getSize(),
					overrides.getOrDefault(ps.getId(), ps.getPrice())),
				Collectors.toList())));
	}

	// ── Escritura (US-SIZE-04) ──────────────────────────────────────────────────
	//
	// Los tres métodos de escritura evictan el cache "menu": desde US-SIZE-F-02 los tamaños
	// y sus precios viajan DENTRO del valor cacheado del menú, así que sin evict el ADMIN
	// cambia un precio y el cliente sigue viendo el viejo hasta que expire el TTL.
	//
	// A propósito SIN @Transactional, mismo criterio que ProductService.updateBranchConfig:
	// combinar @Transactional con @CacheEvict deja sin garantía el orden entre el advisor de
	// transacción y el de cache, y la evicción podría correr antes del commit — una lectura
	// concurrente repoblaría el cache con el dato viejo.

	/**
	 * US-SIZE-04: alta del tamaño de un producto.
	 *
	 * Evicción total (allEntries): un producto se ofrece en todas las sucursales del tenant,
	 * así que el alta afecta a todos los menús — mismo criterio que ProductService.update.
	 */
	@CacheEvict(value = "menu", allEntries = true)
	public ProductSize create(Integer productId, ProductSizeName size, BigDecimal price) {
		Product product = productRepository.findByIdWithCategoryType(productId)
			.orElseThrow(() -> new ProductNotFoundException(productId));

		validateSizeIsSupported(size);
		validateCategoryAllowsSizes(product);

		// La tabla tiene UNIQUE (product_id, size): sin este chequeo el duplicado saldría como
		// un 500 de constraint en vez de un caso de negocio.
		productSizeRepository.findByProductIdAndSize(productId, size).ifPresent(existing -> {
			throw new BusinessException("El producto ya tiene cargado el tamaño " + size);
		});

		return productSizeRepository.save(ProductSize.builder()
			.product(product)
			.size(size)
			.price(price)
			.active(true)
			.build());
	}

	/**
	 * US-SIZE-04: edición de precio y/o baja del tamaño. Ambos campos son opcionales: null
	 * deja el valor como está.
	 *
	 * `active=false` es la baja definitiva desde el punto de vista del catálogo, pero la fila
	 * se conserva: order_item.product_size_id la referencia en los pedidos históricos.
	 */
	@CacheEvict(value = "menu", allEntries = true)
	public ProductSize update(Integer productId, Integer productSizeId, BigDecimal price, Boolean active) {
		ProductSize productSize = findOwnedBy(productId, productSizeId);
		if (price != null) {
			productSize.setPrice(price);
		}
		if (active != null) {
			productSize.setActive(active);
		}
		return productSizeRepository.save(productSize);
	}

	/**
	 * US-SIZE-04: override de precio del tamaño en una sucursal. Mismo patrón que
	 * ProductService.updateBranchConfig — incluido el guard de sucursal desactivada.
	 *
	 * `priceOverride` en null limpia el override: el tamaño vuelve a su precio base. Como la
	 * ausencia de fila es equivalente a un override nulo, en ese caso se borra la fila en vez
	 * de dejarla vacía (ver la nota de no auto-provisión en BranchProductSize).
	 */
	@CacheEvict(value = "menu", key = "#branchId")
	public BranchProductSize updateBranchOverride(Integer branchId, Integer productId, Integer productSizeId,
			BigDecimal priceOverride) {
		Branch branch = branchRepository.findById(branchId)
			.orElseThrow(() -> new BranchNotFoundException(branchId));
		if (!branch.isActive()) {
			throw new BusinessException("No se puede modificar la configuración de una sucursal desactivada");
		}
		ProductSize productSize = findOwnedBy(productId, productSizeId);

		if (priceOverride == null) {
			branchProductSizeRepository.findByBranchIdAndProductSizeId(branchId, productSizeId)
				.ifPresent(branchProductSizeRepository::delete);
			return null;
		}

		BranchProductSize config = branchProductSizeRepository
			.findByBranchIdAndProductSizeId(branchId, productSizeId)
			.orElseGet(() -> BranchProductSize.builder().branch(branch).productSize(productSize).build());
		config.setPriceOverride(priceOverride);
		return branchProductSizeRepository.save(config);
	}

	/**
	 * US-SIZE-F-01: tamaño activo de un producto, si tiene. Optional vacío para todo producto
	 * sin tamaños (todos los que no son pizza).
	 *
	 * Devuelve el primero: hoy sólo puede haber uno activo, porque CHICA es el único tamaño
	 * cargable y la tabla tiene UNIQUE (product_id, size).
	 */
	public Optional<ProductSize> findActiveByProduct(Integer productId) {
		return productSizeRepository.findByProductIdAndActiveTrue(productId).stream().findFirst();
	}

	/**
	 * US-SIZE-F-01: overrides de un tamaño indexados por sucursal. Sin entrada = sin override,
	 * vale el precio base del tamaño.
	 */
	public Map<Integer, BigDecimal> findBranchOverrides(Integer productSizeId) {
		return branchProductSizeRepository.findByProductSizeId(productSizeId).stream()
			.filter(bps -> bps.getPriceOverride() != null)
			.collect(Collectors.toMap(bps -> bps.getBranch().getId(), BranchProductSize::getPriceOverride));
	}

	/**
	 * US-SIZE-04: tamaños de un producto para el backoffice, activos e inactivos — a
	 * diferencia del menú del client, que sólo ve los activos.
	 */
	public List<ProductSize> findByProduct(Integer productId) {
		if (!productRepository.existsById(productId)) {
			throw new ProductNotFoundException(productId);
		}
		return productSizeRepository.findByProductIdOrderByIdAsc(productId);
	}

	// El tamaño debe existir y pertenecer al producto de la ruta: sin esto, un PATCH con el
	// id de un tamaño de otro producto lo editaría igual.
	private ProductSize findOwnedBy(Integer productId, Integer productSizeId) {
		ProductSize productSize = productSizeRepository.findById(productSizeId)
			.orElseThrow(() -> new ProductSizeNotFoundException(productSizeId));
		if (!productSize.getProduct().getId().equals(productId)) {
			throw new BusinessException(
				"El tamaño " + productSizeId + " no pertenece al producto " + productId);
		}
		return productSize;
	}

	// GRANDE es implícito: su precio es siempre product.price y nunca tiene fila propia.
	private void validateSizeIsSupported(ProductSizeName size) {
		if (size != ProductSizeName.CHICA) {
			throw new UnsupportedProductSizeException(
				"El tamaño " + size + " no se carga como fila: el tamaño grande es implícito "
					+ "y su precio es el precio base del producto");
		}
	}

	// Cierra DT-04: la regla "sólo productos de una categoría con allows_sizes pueden tener
	// tamaños" no es expresable como constraint declarativo, así que se valida acá.
	private void validateCategoryAllowsSizes(Product product) {
		Category category = product.getCategory();
		CategoryType type = category != null ? category.getCategoryType() : null;
		if (type == null || !type.isAllowsSizes()) {
			throw new BusinessException(
				"La categoría del producto no admite tamaños: " + product.getName());
		}
	}
}
