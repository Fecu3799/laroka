import { useState, useEffect, useRef, useCallback } from 'react'
import { usePreferredBranch } from '../hooks/usePreferredBranch'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export function BranchSelector({ onSelect }) {
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [userSelectedBranchId, setUserSelectedBranchId] = useState(null)
  const { preferredBranchId, isLoaded, saveBranch } = usePreferredBranch()
  const retryRef = useRef(null)

  const hasValidPreferred =
    isLoaded &&
    preferredBranchId != null &&
    branches.some(b => b.id === preferredBranchId)
  const selectedBranchId = userSelectedBranchId ?? (hasValidPreferred ? preferredBranchId : null)

  const retry = useCallback(() => {
    retryRef.current?.()
  }, [])

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const response = await fetch(`${API_BASE}/branches`)
        if (!response.ok) throw new Error('Error fetching branches')
        const data = await response.json()
        if (!cancelled) {
          setBranches(data)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) setError(err.message || 'Error loading branches')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    retryRef.current = load
    load()
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (selectedBranchId) {
      onSelect(selectedBranchId)
    }
  }, [selectedBranchId, onSelect])

  const handleSelectBranch = (branchId) => {
    setUserSelectedBranchId(branchId)
    saveBranch(branchId)
  }

  if (loading) {
    return <div className="branch-selector"><p>Loading branches...</p></div>
  }

  if (error) {
    return (
      <div className="branch-selector error">
        <p>Error: {error}</p>
        <button onClick={retry}>Retry</button>
      </div>
    )
  }

  return (
    <div className="branch-selector">
      <h2>Select a Branch</h2>
      <div className="branches-list">
        {branches.map(branch => (
          <div
            key={branch.id}
            className={`branch-card ${selectedBranchId === branch.id ? 'selected' : ''}`}
            onClick={() => handleSelectBranch(branch.id)}
          >
            <h3>{branch.name}</h3>
            <p>{branch.address}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
