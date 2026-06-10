import { createContext, useState, useEffect } from 'react'
import useAuth from '../hooks/useAuth'

export const BranchContext = createContext(null)

export default function BranchProvider({ children }) {
  const { role, branchId, branchName } = useAuth()
  const [activeBranchId, setActiveBranchId] = useState(null)
  const [activeBranchName, setActiveBranchName] = useState(null)

  useEffect(() => {
    if (role === 'STAFF' && branchId != null) {
      setActiveBranchId(branchId)
      setActiveBranchName(branchName)
    }
  }, [role, branchId, branchName])

  function setActiveBranch(id, name) {
    setActiveBranchId(id)
    setActiveBranchName(name)
  }

  return (
    <BranchContext.Provider value={{ activeBranchId, activeBranchName, setActiveBranch }}>
      {children}
    </BranchContext.Provider>
  )
}
