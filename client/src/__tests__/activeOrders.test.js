import { describe, it, expect, vi } from 'vitest'
import { readActiveOrders, addActiveOrder, removeActiveOrder } from '../utils/activeOrders'

const KEY = 'pedisur_active_orders'

describe('readActiveOrders', () => {
  it('returns [] when storage is empty', () => {
    expect(readActiveOrders()).toEqual([])
  })

  it('parses object entries correctly', () => {
    localStorage.setItem(KEY, JSON.stringify([{ orderId: 'abc', branchId: 1 }]))
    expect(readActiveOrders()).toEqual([{ orderId: 'abc', branchId: 1 }])
  })

  it('normalises legacy plain-string entries', () => {
    localStorage.setItem(KEY, JSON.stringify(['abc']))
    expect(readActiveOrders()).toEqual([{ orderId: 'abc', branchId: null }])
  })

  it('returns [] on corrupt JSON', () => {
    localStorage.setItem(KEY, '{bad}')
    expect(readActiveOrders()).toEqual([])
  })
})

describe('addActiveOrder', () => {
  it('writes a new entry to storage', () => {
    addActiveOrder('order-1', 2)
    expect(readActiveOrders()).toEqual([{ orderId: 'order-1', branchId: 2 }])
  })

  it('dispatches pedisur_orders_updated event', () => {
    const listener = vi.fn()
    window.addEventListener('pedisur_orders_updated', listener)
    addActiveOrder('order-2', 1)
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('pedisur_orders_updated', listener)
  })

  it('does not duplicate an existing orderId', () => {
    addActiveOrder('order-3', 1)
    addActiveOrder('order-3', 1)
    expect(readActiveOrders()).toHaveLength(1)
  })
})

describe('removeActiveOrder', () => {
  it('removes the matching orderId', () => {
    localStorage.setItem(KEY, JSON.stringify([
      { orderId: 'a', branchId: 1 },
      { orderId: 'b', branchId: 2 },
    ]))
    removeActiveOrder('a')
    expect(readActiveOrders()).toEqual([{ orderId: 'b', branchId: 2 }])
  })

  it('dispatches pedisur_orders_updated event', () => {
    localStorage.setItem(KEY, JSON.stringify([{ orderId: 'x', branchId: 1 }]))
    const listener = vi.fn()
    window.addEventListener('pedisur_orders_updated', listener)
    removeActiveOrder('x')
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('pedisur_orders_updated', listener)
  })
})
