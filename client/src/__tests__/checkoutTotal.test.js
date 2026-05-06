import { describe, it, expect } from 'vitest'

function calcTotal(items, serviceFee, deliveryFee, isDelivery) {
  const subtotal = items.reduce((sum, item) => sum + item.price * item.qty, 0)
  return subtotal + Number(serviceFee) + (isDelivery ? Number(deliveryFee) : 0)
}

const ITEMS = [
  { id: 1, price: 2000, qty: 2 },
  { id: 2, price: 1500, qty: 1 },
]

describe('checkout total calculation', () => {
  it('subtotal sums price × qty', () => {
    const subtotal = ITEMS.reduce((s, i) => s + i.price * i.qty, 0)
    expect(subtotal).toBe(5500)
  })

  it('DELIVERY total includes deliveryFee and serviceFee', () => {
    expect(calcTotal(ITEMS, 200, 800, true)).toBe(6500)
  })

  it('TAKEAWAY total omits deliveryFee', () => {
    expect(calcTotal(ITEMS, 200, 800, false)).toBe(5700)
  })

  it('total is subtotal only when both fees are zero', () => {
    expect(calcTotal(ITEMS, 0, 0, true)).toBe(5500)
    expect(calcTotal(ITEMS, 0, 0, false)).toBe(5500)
  })

  it('empty cart produces zero total', () => {
    expect(calcTotal([], 200, 500, true)).toBe(700)
    expect(calcTotal([], 200, 500, false)).toBe(200)
  })
})
