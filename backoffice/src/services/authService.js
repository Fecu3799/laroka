const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function login(email, password) {
  return fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
}

export function logout() {
  localStorage.removeItem('laroka_token')
}
