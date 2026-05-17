const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchBranchMenu(branchId, token) {
  const res = await fetch(`${API_URL}/branches/${branchId}/menu`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
