import { useState, useEffect } from 'react'

function decodeToken(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    if (typeof payload.exp !== 'number') return null
    if (payload.exp * 1000 <= Date.now()) return 'expired'
    return payload
  } catch {
    return null
  }
}

export default function useAuth() {
  const [auth, setAuth] = useState(() => {
    const token = localStorage.getItem('laroka_token')
    return token ? decodeToken(token) : null
  })

  useEffect(() => {
    function handleStorage(e) {
      if (e.key === 'laroka_token') {
        const token = e.newValue
        setAuth(token ? decodeToken(token) : null)
      }
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const isExpired = auth === 'expired'
  const payload = auth !== null && auth !== 'expired' ? auth : null

  return {
    userId: payload?.sub ?? payload?.userId ?? null,
    role: payload?.role ?? null,
    branchId: payload?.branchId ?? null,
    branchName: payload?.branchName ?? null,
    tenantId: payload?.tenantId ?? null,
    tenantName: payload?.tenantName ?? null,
    isAuthenticated: payload !== null,
    isExpired,
  }
}
