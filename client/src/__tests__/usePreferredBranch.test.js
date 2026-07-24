import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { usePreferredBranch } from '../hooks/usePreferredBranch'

const BRANCH = { id: 3, name: 'Centro', deliveryFee: 500, serviceFee: 100, phone: '2804000000', estimatedDeliveryMinutes: 30 }

describe('usePreferredBranch', () => {
  it('returns nulls when storage is empty', () => {
    const { result } = renderHook(() => usePreferredBranch())
    expect(result.current.preferredBranchId).toBeNull()
    expect(result.current.preferredBranchName).toBeNull()
    expect(result.current.deliveryFee).toBe(0)
    expect(result.current.serviceFee).toBe(0)
    expect(result.current.isLoaded).toBe(true)
  })

  it('reads saved branch from localStorage on init', () => {
    localStorage.setItem('pedisur_preferred_branch', JSON.stringify(BRANCH))
    const { result } = renderHook(() => usePreferredBranch())
    expect(result.current.preferredBranchId).toBe(3)
    expect(result.current.preferredBranchName).toBe('Centro')
    expect(result.current.deliveryFee).toBe(500)
    expect(result.current.estimatedDeliveryMinutes).toBe(30)
  })

  it('saveBranch persists to localStorage and updates state', () => {
    const { result } = renderHook(() => usePreferredBranch())
    act(() => result.current.saveBranch(BRANCH))
    expect(result.current.preferredBranchId).toBe(3)
    const stored = JSON.parse(localStorage.getItem('pedisur_preferred_branch'))
    expect(stored.id).toBe(3)
  })

  it('clearBranch resets state and removes localStorage entry', () => {
    localStorage.setItem('pedisur_preferred_branch', JSON.stringify(BRANCH))
    const { result } = renderHook(() => usePreferredBranch())
    act(() => result.current.clearBranch())
    expect(result.current.preferredBranchId).toBeNull()
    expect(localStorage.getItem('pedisur_preferred_branch')).toBeNull()
  })

  it('handles legacy plain numeric string in storage', () => {
    localStorage.setItem('pedisur_preferred_branch', '5')
    const { result } = renderHook(() => usePreferredBranch())
    expect(result.current.preferredBranchId).toBe(5)
  })

  it('handles corrupt JSON gracefully', () => {
    localStorage.setItem('pedisur_preferred_branch', '{bad json}')
    const { result } = renderHook(() => usePreferredBranch())
    expect(result.current.preferredBranchId).toBeNull()
  })
})
