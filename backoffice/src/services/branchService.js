const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchBranches(token) {
  const res = await fetch(`${API_URL}/branches`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
