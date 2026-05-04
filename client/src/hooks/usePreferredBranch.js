import { useState } from 'react'

const STORAGE_KEY = 'laroka_preferred_branch'

function readStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { id: null, name: null, deliveryFee: 0, serviceFee: 0 }
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object') {
      if (parsed.deliveryFee == null || parsed.serviceFee == null) {
        localStorage.removeItem(STORAGE_KEY)
        return { id: null, name: null, deliveryFee: 0, serviceFee: 0 }
      }
      return {
        id: parsed.id ?? null,
        name: parsed.name ?? null,
        deliveryFee: Number(parsed.deliveryFee) || 0,
        serviceFee: Number(parsed.serviceFee) || 0,
      }
    }
    // legacy: plain numeric string
    const id = parseInt(raw, 10)
    return { id: isNaN(id) ? null : id, name: null, deliveryFee: 0, serviceFee: 0 }
  } catch {
    return { id: null, name: null, deliveryFee: 0, serviceFee: 0 }
  }
}

export function usePreferredBranch() {
  const [state, setState] = useState(() => {
    const { id, name, deliveryFee, serviceFee } = readStorage()
    return { preferredBranchId: id, preferredBranchName: name, deliveryFee, serviceFee, isLoaded: true }
  })

  const saveBranch = ({ id, name, deliveryFee, serviceFee }) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ id, name, deliveryFee, serviceFee }))
    setState(prev => ({ ...prev, preferredBranchId: id, preferredBranchName: name, deliveryFee, serviceFee }))
  }

  const clearBranch = () => {
    localStorage.removeItem(STORAGE_KEY)
    setState(prev => ({ ...prev, preferredBranchId: null, preferredBranchName: null, deliveryFee: 0, serviceFee: 0 }))
  }

  return {
    preferredBranchId: state.preferredBranchId,
    preferredBranchName: state.preferredBranchName,
    deliveryFee: state.deliveryFee,
    serviceFee: state.serviceFee,
    isLoaded: state.isLoaded,
    saveBranch,
    clearBranch
  }
}
