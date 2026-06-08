import { useState, useCallback, useEffect } from 'react'

const CART_KEY = 'laroka_cart'

function loadInitialItems(initialItems) {
  if (initialItems.length > 0) return initialItems
  try {
    const raw = localStorage.getItem(CART_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch {
    return []
  }
}

export function useCart(initialItems = []) {
  const [items, setItems] = useState(() => loadInitialItems(initialItems))

  useEffect(() => {
    localStorage.setItem(CART_KEY, JSON.stringify(items))
  }, [items])

  const addItem = useCallback((product, qty = 1) => {
    setItems((prev) => {
      const existing = prev.find((i) => i.id === product.id)
      if (existing) {
        return prev.map((i) =>
          i.id === product.id ? { ...i, qty: i.qty + qty } : i,
        )
      }
      return [...prev, { ...product, qty }]
    })
  }, [])

  const removeItem = useCallback((productId) => {
    setItems((prev) => prev.filter((i) => i.id !== productId))
  }, [])

  const updateQty = useCallback((productId, qty) => {
    if (qty < 1) return
    setItems((prev) =>
      prev.map((i) => (i.id === productId ? { ...i, qty } : i)),
    )
  }, [])

  const clearCart = useCallback(() => {
    localStorage.setItem(CART_KEY, JSON.stringify([]))
    setItems([])
  }, [])

  const total = items.reduce((sum, i) => sum + i.price * i.qty, 0)
  const count = items.reduce((sum, i) => sum + i.qty, 0)

  return { items, addItem, removeItem, updateQty, clearCart, total, count }
}
