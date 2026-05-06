import { useState } from 'react'

const STORAGE_KEY = 'laroka_preferred_branch'

function readStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { id: null, name: null, deliveryFee: 0, serviceFee: 0, phone: null, estimatedDeliveryMinutes: null }
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && parsed.id != null) {
      return {
        id: parsed.id,
        name: parsed.name ?? null,
        deliveryFee: Number(parsed.deliveryFee) || 0,
        serviceFee: Number(parsed.serviceFee) || 0,
        phone: parsed.phone ?? null,
        estimatedDeliveryMinutes: parsed.estimatedDeliveryMinutes ?? null,
      }
    }
    // legacy: plain numeric string
    const id = parseInt(raw, 10)
    return { id: isNaN(id) ? null : id, name: null, deliveryFee: 0, serviceFee: 0, phone: null, estimatedDeliveryMinutes: null }
  } catch {
    return { id: null, name: null, deliveryFee: 0, serviceFee: 0, phone: null, estimatedDeliveryMinutes: null }
  }
}

export function usePreferredBranch() {
  const [state, setState] = useState(() => {
    const { id, name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes } = readStorage()
    return { preferredBranchId: id, preferredBranchName: name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes, isLoaded: true }
  })

  const saveBranch = ({ id, name, deliveryFee, serviceFee, phone = null, estimatedDeliveryMinutes = null }) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ id, name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes }))
    setState(prev => ({ ...prev, preferredBranchId: id, preferredBranchName: name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes }))
  }

  const clearBranch = () => {
    localStorage.removeItem(STORAGE_KEY)
    setState(prev => ({ ...prev, preferredBranchId: null, preferredBranchName: null, deliveryFee: 0, serviceFee: 0, phone: null, estimatedDeliveryMinutes: null }))
  }

  return {
    preferredBranchId: state.preferredBranchId,
    preferredBranchName: state.preferredBranchName,
    deliveryFee: state.deliveryFee,
    serviceFee: state.serviceFee,
    phone: state.phone,
    estimatedDeliveryMinutes: state.estimatedDeliveryMinutes,
    isLoaded: state.isLoaded,
    saveBranch,
    clearBranch,
  }
}
