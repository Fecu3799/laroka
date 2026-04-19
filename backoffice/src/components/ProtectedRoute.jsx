import { Navigate } from 'react-router-dom'

export default function ProtectedRoute({ children }) {
  // TODO: In Sprint 2-F, replace this with actual JWT token validation
  // Check if token exists in localStorage and is valid
  const isAuthenticated = localStorage.getItem('laroka_token')

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return children
}
