import { describe, it, expect } from 'vitest'
import {
  buildHalfAndHalfItem,
  buildSizedItem,
  cartItemProductId,
  halfAndHalfCartId,
  halfAndHalfName,
  halfAndHalfPrice,
  isHalfAndHalfItem,
  orderItemDisplayName,
} from '../utils/halfAndHalf'

const MUZZA = { id: 1, name: 'Muzzarella', price: 2800, imageUrl: 'muzza.jpg' }
const NAPO = { id: 2, name: 'Napolitana', price: 3200, imageUrl: 'napo.jpg' }

describe('halfAndHalfPrice', () => {
  // US-HH-03: el backend cobra el mayor de los dos precios efectivos. El preview usa la
  // misma regla sobre el price del menú (que ya viene resuelto con priceOverride).
  it('usa el precio de la mitad más cara sin importar el orden', () => {
    expect(halfAndHalfPrice(MUZZA, NAPO)).toBe(3200)
    expect(halfAndHalfPrice(NAPO, MUZZA)).toBe(3200)
  })

  it('no suma ni promedia los dos precios', () => {
    expect(halfAndHalfPrice(MUZZA, NAPO)).not.toBe(MUZZA.price + NAPO.price)
    expect(halfAndHalfPrice(MUZZA, NAPO)).not.toBe((MUZZA.price + NAPO.price) / 2)
  })

  it('con precios iguales devuelve ese precio', () => {
    expect(halfAndHalfPrice(MUZZA, { ...NAPO, price: 2800 })).toBe(2800)
  })
})

describe('halfAndHalfCartId', () => {
  it('es el mismo par sin importar desde qué mitad se armó', () => {
    // Elegir Muzza+Napo desde el detalle de Muzza o desde el de Napo debe caer en la misma
    // línea del carrito, no generar dos entradas equivalentes.
    expect(halfAndHalfCartId(1, 2)).toBe(halfAndHalfCartId(2, 1))
  })

  it('no colisiona con el id de un producto suelto', () => {
    expect(halfAndHalfCartId(1, 2)).not.toBe(1)
    expect(typeof halfAndHalfCartId(1, 2)).toBe('string')
  })
})

describe('buildHalfAndHalfItem', () => {
  it('arma el ítem con ambos productos, el nombre combinado y el mayor precio', () => {
    const item = buildHalfAndHalfItem(MUZZA, NAPO)

    expect(item.productId).toBe(1)
    expect(item.secondProductId).toBe(2)
    expect(item.name).toBe('½ Muzzarella + ½ Napolitana')
    expect(item.price).toBe(3200)
    expect(isHalfAndHalfItem(item)).toBe(true)
  })

  it('conserva los nombres por separado para el armado del carrito', () => {
    const item = buildHalfAndHalfItem(MUZZA, NAPO)

    expect(item.productName).toBe('Muzzarella')
    expect(item.secondProductName).toBe('Napolitana')
  })
})

describe('halfAndHalfName', () => {
  it('respeta el orden en que se eligieron las mitades', () => {
    expect(halfAndHalfName(NAPO, MUZZA)).toBe('½ Napolitana + ½ Muzzarella')
  })
})

describe('cartItemProductId', () => {
  it('para un ítem combinado devuelve el productId de la primera mitad', () => {
    expect(cartItemProductId(buildHalfAndHalfItem(MUZZA, NAPO))).toBe(1)
  })

  it('para un ítem simple devuelve su propio id', () => {
    expect(cartItemProductId({ id: 7, name: 'Fugazzeta', price: 3000 })).toBe(7)
  })

  it('para un ítem persistido antes de esta historia (sin productId) usa el id', () => {
    // Retrocompatibilidad: carritos ya guardados en localStorage no tienen productId.
    expect(cartItemProductId({ id: 9, productId: undefined })).toBe(9)
    expect(cartItemProductId({ id: 9, productId: null })).toBe(9)
  })

  it('un ítem simple no se considera mitad y mitad', () => {
    expect(isHalfAndHalfItem({ id: 7, name: 'Fugazzeta' })).toBe(false)
  })
})

describe('orderItemDisplayName', () => {
  // Los ítems de GET /orders/{id}/items vienen como { name, secondProductName }, no como
  // dos productos — pero el formato mostrado debe ser el mismo que en carrito y checkout.
  it('arma la combinación con el mismo formato que el carrito', () => {
    const apiItem = { name: 'Muzzarella', secondProductName: 'Calabresa', quantity: 1 }

    expect(orderItemDisplayName(apiItem)).toBe('½ Muzzarella + ½ Calabresa')
    expect(orderItemDisplayName(apiItem)).toBe(
      halfAndHalfName({ name: 'Muzzarella' }, { name: 'Calabresa' }),
    )
  })

  it('deja el nombre tal cual en un ítem simple', () => {
    expect(orderItemDisplayName({ name: 'Fugazzeta', secondProductName: null })).toBe('Fugazzeta')
    expect(orderItemDisplayName({ name: 'Fugazzeta' })).toBe('Fugazzeta')
  })
})

describe('buildSizedItem', () => {
  const MUZZA_SIZES = { id: 1, name: 'Muzzarella', price: 2800, imageUrl: 'muzza.jpg' }
  const CHICA = { id: 50, size: 'CHICA', price: 1900 }

  it('lleva el productSizeId y el precio del tamaño, no el del producto', () => {
    const item = buildSizedItem(MUZZA_SIZES, CHICA)

    expect(item.productId).toBe(1)
    expect(item.productSizeId).toBe(50)
    expect(item.price).toBe(1900)
    expect(item.name).toBe('Muzzarella (Chica)')
  })

  it('no comparte identidad con el producto suelto ni con otro tamaño', () => {
    const chica = buildSizedItem(MUZZA_SIZES, CHICA)
    const otra = buildSizedItem(MUZZA_SIZES, { id: 51, size: 'CHICA', price: 2000 })

    expect(chica.id).not.toBe(MUZZA_SIZES.id)
    expect(chica.id).not.toBe(otra.id)
  })

  it('cartItemProductId devuelve el producto base, no el id del carrito', () => {
    expect(cartItemProductId(buildSizedItem(MUZZA_SIZES, CHICA))).toBe(1)
  })
})
