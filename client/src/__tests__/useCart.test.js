import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCart } from '../hooks/useCart'

const p1 = { id: 1, name: 'Muzarella', price: 1000 }
const p2 = { id: 2, name: 'Napolitana', price: 1200 }

describe('useCart', () => {
  it('starts empty', () => {
    const { result } = renderHook(() => useCart())
    expect(result.current.items).toHaveLength(0)
    expect(result.current.total).toBe(0)
    expect(result.current.count).toBe(0)
  })

  it('addItem creates a new entry', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0]).toMatchObject({ id: 1, qty: 1 })
  })

  it('addItem increments qty for existing product', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.addItem(p1, 2))
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0].qty).toBe(3)
  })

  it('removeItem removes the product', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.addItem(p2))
    act(() => result.current.removeItem(p1.id))
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0].id).toBe(2)
  })

  it('updateQty changes qty', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.updateQty(p1.id, 5))
    expect(result.current.items[0].qty).toBe(5)
  })

  it('updateQty ignores qty < 1', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.updateQty(p1.id, 0))
    expect(result.current.items[0].qty).toBe(1)
  })

  it('clearCart empties items', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.clearCart())
    expect(result.current.items).toHaveLength(0)
  })

  it('total sums price * qty across items', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1, 2)) // 2000
    act(() => result.current.addItem(p2, 1)) // 1200
    expect(result.current.total).toBe(3200)
  })

  it('count sums qty across items', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1, 3))
    act(() => result.current.addItem(p2, 2))
    expect(result.current.count).toBe(5)
  })

  it('total updates when qty changes', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    expect(result.current.total).toBe(1000)
    act(() => result.current.updateQty(p1.id, 3))
    expect(result.current.total).toBe(3000)
  })

  it('total updates when item is removed', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.addItem(p1))
    act(() => result.current.addItem(p2))
    act(() => result.current.removeItem(p1.id))
    expect(result.current.total).toBe(1200)
  })
})
