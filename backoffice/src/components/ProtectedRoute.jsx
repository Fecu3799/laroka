import { Navigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, isExpired } = useAuth()

  if (isExpired) return <Navigate to="/login?reason=expired" replace />
  if (!isAuthenticated) return <Navigate to="/login" replace />

  return children
}
