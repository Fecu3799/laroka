import { useState, useEffect } from 'react'

const STORAGE_KEY = 'laroka_preferred_branch'

export function usePreferredBranch() {
  const [preferredBranchId, setPreferredBranchId] = useState(null)
  const [isLoaded, setIsLoaded] = useState(false)

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      setPreferredBranchId(parseInt(stored))
    }
    setIsLoaded(true)
  }, [])

  const saveBranch = (branchId) => {
    localStorage.setItem(STORAGE_KEY, branchId.toString())
    setPreferredBranchId(branchId)
  }

  const clearBranch = () => {
    localStorage.removeItem(STORAGE_KEY)
    setPreferredBranchId(null)
  }

  return {
    preferredBranchId,
    isLoaded,
    saveBranch,
    clearBranch
  }
}
