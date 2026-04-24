import { Navigate } from 'react-router-dom'

function isTokenValid(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now()
  } catch {
    return false
  }
}

export default function ProtectedRoute({ children }) {
  const token = localStorage.getItem('laroka_token')

  if (!token || !isTokenValid(token)) {
    return <Navigate to="/login" replace />
  }

  return children
}
