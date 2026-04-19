import { useState } from 'react'

const STORAGE_KEY = 'laroka_preferred_branch'

export function usePreferredBranch() {
  const [state, setState] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return {
      preferredBranchId: stored ? parseInt(stored) : null,
      isLoaded: true
    }
  })

  const saveBranch = (branchId) => {
    localStorage.setItem(STORAGE_KEY, branchId.toString())
    setState(prev => ({ ...prev, preferredBranchId: branchId }))
  }

  const clearBranch = () => {
    localStorage.removeItem(STORAGE_KEY)
    setState(prev => ({ ...prev, preferredBranchId: null }))
  }

  return {
    preferredBranchId: state.preferredBranchId,
    isLoaded: state.isLoaded,
    saveBranch,
    clearBranch
  }
}
