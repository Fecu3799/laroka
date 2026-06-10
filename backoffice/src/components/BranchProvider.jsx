import { useState, useEffect } from 'react'
import useAuth from '../hooks/useAuth'
import { BranchContext } from '../hooks/useBranch'

const SESSION_KEY = 'laroka_active_branch'

function readSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export default function BranchProvider({ children }) {
  const { role, branchId, branchName } = useAuth()
  const [activeBranchId, setActiveBranchId] = useState(() => readSession()?.id ?? null)
  const [activeBranchName, setActiveBranchName] = useState(() => readSession()?.name ?? null)

  useEffect(() => {
    if (role === 'STAFF' && branchId != null) {
      setActiveBranchId(branchId)
      setActiveBranchName(branchName)
    }
  }, [role, branchId, branchName])

  function setActiveBranch(id, name) {
    setActiveBranchId(id)
    setActiveBranchName(name)
    try {
      if (id != null) {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify({ id, name }))
      } else {
        sessionStorage.removeItem(SESSION_KEY)
      }
    } catch { /* noop */ }
  }

  return (
    <BranchContext.Provider value={{ activeBranchId, activeBranchName, setActiveBranch }}>
      {children}
    </BranchContext.Provider>
  )
}
