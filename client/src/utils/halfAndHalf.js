// US-HH-F-01: helpers del ítem mitad y mitad, compartidos entre el detalle de producto
// (armado del ítem) y el carrito/checkout (payload al backend).

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

// Id de producto que viaja al backend por ítem del carrito. Los ítems simples no tienen
// productId propio: su id ES el del producto (carritos ya persistidos en localStorage
// anteriores a esta historia incluidos).
export function cartItemProductId(item) {
  return item.productId ?? item.id
}
