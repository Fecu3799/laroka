import { useState, useCallback } from 'react'
import { SplashScreen } from './components/SplashScreen'
import { BranchSelection } from './components/BranchSelection'
import { usePreferredBranch } from './hooks/usePreferredBranch'
import './App.css'

function App() {
  const { preferredBranchId, saveBranch, clearBranch } = usePreferredBranch()
  const [screen, setScreen] = useState('splash')
  const [selectedBranchId, setSelectedBranchId] = useState(preferredBranchId)

  const handleSplashComplete = useCallback(() => {
    if (preferredBranchId) {
      setScreen('menu')
    } else {
      setScreen('selection')
    }
  }, [preferredBranchId])

  const handleBranchSelect = useCallback((branchId) => {
    saveBranch(branchId)
    setSelectedBranchId(branchId)
    setScreen('menu')
  }, [saveBranch])

  const handleChangeBranch = useCallback(() => {
    clearBranch()
    setSelectedBranchId(null)
    setScreen('selection')
  }, [clearBranch])

  if (screen === 'splash') {
    return <SplashScreen onComplete={handleSplashComplete} />
  }

  if (screen === 'selection') {
    return <BranchSelection onSelect={handleBranchSelect} />
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <h1 className="app-header-title">La Roka</h1>
        <button className="change-branch-btn" onClick={handleChangeBranch}>
          Cambiar sucursal
        </button>
      </header>
      <main className="app-main">
        <p>Sucursal seleccionada: {selectedBranchId}</p>
      </main>
    </div>
  )
}

export default App
