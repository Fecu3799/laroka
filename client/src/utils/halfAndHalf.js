// Identidad y armado de los ítems del carrito que llevan opciones: mitad y mitad
// (US-HH-F-01) y tamaño (US-SIZE-F-02). Compartido entre el detalle de producto (armado) y
// el carrito/checkout (payload al backend).

// Identidad del ítem combinado dentro del carrito. Se normaliza ordenando los ids para que
// elegir el mismo par desde el detalle de A o desde el de B caiga en la misma línea del
// carrito en vez de generar dos entradas equivalentes.
export function halfAndHalfCartId(firstId, secondId) {
  const [a, b] = [Number(firstId), Number(secondId)].sort((x, y) => x - y)
  return `hh-${a}-${b}`
}

export function isHalfAndHalfItem(item) {
  return item?.secondProductId != null
}

// Regla de precio de US-HH-03 replicada para el preview: el ítem combinado vale el mayor de
// los dos precios efectivos de la sucursal. El menú ya expone `price` como precio efectivo
// (priceOverride ?? price), así que comparar `price` da exactamente lo que el backend
// resuelve al confirmar el pedido — el preview nunca difiere del cobro real.
export function halfAndHalfPrice(first, second) {
  return Math.max(Number(first.price), Number(second.price))
}

export function halfAndHalfName(first, second) {
  return `½ ${first.name} + ½ ${second.name}`
}

// US-HH-F-02: nombre a mostrar para un ítem que viene del backend
// (GET /orders/{id}/items), cuya forma es { name, secondProductName } en vez de dos
// productos. Delega en halfAndHalfName para no duplicar el formato "½ A + ½ B".
export function orderItemDisplayName(item) {
  return item.secondProductName
    ? halfAndHalfName({ name: item.name }, { name: item.secondProductName })
    : item.name
}

export function buildHalfAndHalfItem(first, second) {
  return {
    id: halfAndHalfCartId(first.id, second.id),
    productId: first.id,
    secondProductId: second.id,
    productName: first.name,
    secondProductName: second.name,
    name: halfAndHalfName(first, second),
    price: halfAndHalfPrice(first, second),
    imageUrl: first.imageUrl || second.imageUrl || null,
    description: null,
  }
}

// US-SIZE-F-02: ítem con tamaño elegido. Identidad propia para que no se fusione con el
// mismo producto en otro tamaño ni con el producto sin tamaño ("Grande", que es el
// comportamiento por defecto y no lleva productSizeId).
export function sizedCartId(productId, productSizeId) {
  return `size-${productId}-${productSizeId}`
}

export function buildSizedItem(product, size) {
  return {
    id: sizedCartId(product.id, size.id),
    productId: product.id,
    productSizeId: size.id,
    sizeName: size.size,
    name: `${product.name} (${sizeLabel(size.size)})`,
    price: Number(size.price),
    imageUrl: product.imageUrl || null,
    description: null,
  }
}

// Etiqueta visible de un tamaño. El backend lo expone como enum (CHICA / GRANDE).
export function sizeLabel(size) {
  if (size === 'CHICA') return 'Chica'
  if (size === 'GRANDE') return 'Grande'
  return size
}

// Id de producto que viaja al backend por ítem del carrito. Los ítems simples no tienen
// productId propio: su id ES el del producto (carritos ya persistidos en localStorage
// anteriores a esta historia incluidos).
export function cartItemProductId(item) {
  return item.productId ?? item.id
}
