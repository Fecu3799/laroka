import { useState } from 'react'
import { BranchSelector } from './components/BranchSelector'
import './App.css'

function App() {
  const [selectedBranchId, setSelectedBranchId] = useState(null)

  const handleBranchSelect = (branchId) => {
    setSelectedBranchId(branchId)
  }

  if (!selectedBranchId) {
    return <BranchSelector onSelect={handleBranchSelect} />
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>LaRoka</h1>
        <button
          className="change-branch-btn"
          onClick={() => setSelectedBranchId(null)}
        >
          Change Branch
        </button>
      </header>
      <main className="app-main">
        <p>Selected Branch ID: {selectedBranchId}</p>
      </main>
    </div>
  )
}

export default App
