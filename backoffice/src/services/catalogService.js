import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchBranchMenu(branchId, token) {
  const res = await apiFetch(`${API_URL}/branches/${branchId}/menu`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}
