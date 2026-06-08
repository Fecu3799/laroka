const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function login(email, password) {
  return fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
}

export function logout(token) {
  const refreshToken = localStorage.getItem('laroka_refresh_token')
  if (token) {
    fetch(`${API_URL}/auth/logout`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    }).catch(() => {})
  }
  localStorage.removeItem('laroka_token')
  localStorage.removeItem('laroka_refresh_token')
}
