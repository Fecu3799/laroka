import { useState, useEffect } from 'react'
import { usePreferredBranch } from '../hooks/usePreferredBranch'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export function BranchSelector({ onSelect }) {
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedBranchId, setSelectedBranchId] = useState(null)
  const { preferredBranchId, isLoaded, saveBranch } = usePreferredBranch()

  useEffect(() => {
    fetchBranches()
  }, [])

  useEffect(() => {
    if (isLoaded && preferredBranchId && branches.length > 0) {
      const exists = branches.some(b => b.id === preferredBranchId)
      if (exists) {
        setSelectedBranchId(preferredBranchId)
        onSelect(preferredBranchId)
      }
    }
  }, [isLoaded, preferredBranchId, branches, onSelect])

  const fetchBranches = async () => {
    try {
      setLoading(true)
      setError(null)
      const response = await fetch(`${API_BASE}/branches`)
      if (!response.ok) throw new Error('Error fetching branches')
      const data = await response.json()
      setBranches(data)
    } catch (err) {
      setError(err.message || 'Error loading branches')
    } finally {
      setLoading(false)
    }
  }

  const handleSelectBranch = (branchId) => {
    setSelectedBranchId(branchId)
    saveBranch(branchId)
    onSelect(branchId)
  }

  if (loading) {
    return <div className="branch-selector"><p>Loading branches...</p></div>
  }

  if (error) {
    return (
      <div className="branch-selector error">
        <p>Error: {error}</p>
        <button onClick={fetchBranches}>Retry</button>
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
