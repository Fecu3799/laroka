import { useState } from 'react'

const STORAGE_KEY = 'laroka_preferred_branch'

function readStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { id: null, name: null }
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object') return { id: parsed.id ?? null, name: parsed.name ?? null }
    // legacy: plain numeric string
    const id = parseInt(raw, 10)
    return { id: isNaN(id) ? null : id, name: null }
  } catch {
    return { id: null, name: null }
  }
}

export function usePreferredBranch() {
  const [state, setState] = useState(() => {
    const { id, name } = readStorage()
    return { preferredBranchId: id, preferredBranchName: name, isLoaded: true }
  })

  const saveBranch = (id, name) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ id, name }))
    setState(prev => ({ ...prev, preferredBranchId: id, preferredBranchName: name }))
  }

  const clearBranch = () => {
    localStorage.removeItem(STORAGE_KEY)
    setState(prev => ({ ...prev, preferredBranchId: null, preferredBranchName: null }))
  }

  return {
    preferredBranchId: state.preferredBranchId,
    preferredBranchName: state.preferredBranchName,
    isLoaded: state.isLoaded,
    saveBranch,
    clearBranch
  }
}
