import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import useAuth from '../hooks/useAuth'
import { fetchCategories, fetchProducts } from '../services/catalogService'
import { fetchBackofficeBranches } from '../services/branchService'
import { fetchTenantProfile } from '../services/tenantService'
import { fetchStaffUsers } from '../services/staffService'

const ConfigContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useConfig() {
  const ctx = useContext(ConfigContext)
  if (!ctx) throw new Error('useConfig debe usarse dentro de <ConfigProvider>')
  return ctx
}

// Cache de datos globales del tenant (US-14-F-05). Vive en Layout y persiste
// durante toda la sesión, por lo que categorías, productos, sucursales, perfil y
// staff se cargan una sola vez y sobreviven a la navegación entre pestañas.
//
// La carga se gatea por rol para no pegarle a endpoints que el rol no puede
// consumir (apiFetch dispara un toast ante cualquier 4xx):
//   - categorías y productos → ADMIN y MANAGER (pestaña Menú)
//   - sucursales, perfil del tenant y staff users → solo ADMIN (pestaña CONFIG)
// STAFF no consume ninguno: no carga nada.
export function ConfigProvider({ children }) {
  const { token, role, tenantId } = useAuth()

  const [categories, setCategories] = useState([])
  const [products, setProducts] = useState([])
  const [branches, setBranches] = useState([])
  const [tenantProfile, setTenantProfile] = useState(null)
  const [staffUsers, setStaffUsers] = useState([])
  const [loadingConfig, setLoadingConfig] = useState(true)

  const canCatalog = role === 'ADMIN' || role === 'MANAGER'
  const isAdmin = role === 'ADMIN'

  // Invalidaciones: re-fetch y reemplazo en sitio. No reseteamos a null/[] antes
  // de pedir, para evitar flashes de contenido vacío tras una mutación.
  const reloadCategories = useCallback(async () => {
    if (!token || !canCatalog) return
    try { setCategories(await fetchCategories(token, tenantId)) } catch { /* apiFetch ya togglea el toast */ }
  }, [token, canCatalog, tenantId])

  const reloadProducts = useCallback(async () => {
    if (!token || !canCatalog) return
    try { setProducts(await fetchProducts(token, tenantId)) } catch { /* noop */ }
  }, [token, canCatalog, tenantId])

  const reloadBranches = useCallback(async () => {
    if (!token || !isAdmin) return
    try { setBranches(await fetchBackofficeBranches(token, tenantId)) } catch { /* noop */ }
  }, [token, isAdmin, tenantId])

  const reloadTenantProfile = useCallback(async () => {
    if (!token || !isAdmin) return
    try { setTenantProfile(await fetchTenantProfile(token)) } catch { /* noop */ }
  }, [token, isAdmin])

  const reloadStaffUsers = useCallback(async () => {
    if (!token || !isAdmin) return
    try { setStaffUsers(await fetchStaffUsers(token)) } catch { /* noop */ }
  }, [token, isAdmin])

  // Carga inicial única por sesión (y al cambiar token/tenant/rol).
  useEffect(() => {
    if (!token || !role) return
    if (!canCatalog) { setLoadingConfig(false); return }
    let cancelled = false
    setLoadingConfig(true)
    Promise.all([
      fetchCategories(token, tenantId).catch(() => []),
      fetchProducts(token, tenantId).catch(() => []),
      isAdmin ? fetchBackofficeBranches(token, tenantId).catch(() => []) : Promise.resolve([]),
      isAdmin ? fetchTenantProfile(token).catch(() => null) : Promise.resolve(null),
      isAdmin ? fetchStaffUsers(token).catch(() => []) : Promise.resolve([]),
    ]).then(([cats, prods, brs, profile, staff]) => {
      if (cancelled) return
      setCategories(cats)
      setProducts(prods)
      setBranches(brs)
      setTenantProfile(profile)
      setStaffUsers(staff)
    }).finally(() => { if (!cancelled) setLoadingConfig(false) })
    return () => { cancelled = true }
  }, [token, role, canCatalog, isAdmin, tenantId])

  const value = {
    categories,
    products,
    branches,
    tenantProfile,
    staffUsers,
    loadingConfig,
    reloadCategories,
    reloadProducts,
    reloadBranches,
    reloadTenantProfile,
    reloadStaffUsers,
  }

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>
}
