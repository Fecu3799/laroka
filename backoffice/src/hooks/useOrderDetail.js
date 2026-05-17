import { useState, useEffect, useCallback } from 'react'
import { fetchOrderDetail } from '../services/ordersService'

export default function useOrderDetail(selectedId, token) {
  const [detail, setDetail] = useState(null)

  const load = useCallback(async () => {
    if (!selectedId || !token) { setDetail(null); return }
    try {
      const data = await fetchOrderDetail(selectedId, token)
      setDetail(data)
    } catch { /* silent */ }
  }, [selectedId, token])

  useEffect(() => { load() }, [load])

  return { detail, refetchDetail: load }
}
