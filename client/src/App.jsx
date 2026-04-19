import { useState, useCallback, useRef, useEffect } from 'react'
import { SplashScreen } from './components/SplashScreen'
import { BranchSelection } from './components/BranchSelection'
import { usePreferredBranch } from './hooks/usePreferredBranch'
import './App.css'

const SCREEN_EXIT_DURATION = 320

function App() {
  const { preferredBranchId, saveBranch, clearBranch } = usePreferredBranch()
  const [screen, setScreen] = useState('splash')
  const [selectedBranchId, setSelectedBranchId] = useState(preferredBranchId)
  const [exiting, setExiting] = useState(false)
  const exitTimerRef = useRef(null)

  useEffect(() => () => clearTimeout(exitTimerRef.current), [])

  const handleSplashComplete = useCallback(() => {
    setScreen(preferredBranchId ? 'menu' : 'selection')
  }, [preferredBranchId])

  const handleBranchSelect = useCallback((branchId) => {
    saveBranch(branchId)
    setSelectedBranchId(branchId)
    setExiting(true)
    exitTimerRef.current = setTimeout(() => {
      setScreen('menu')
      setExiting(false)
    }, SCREEN_EXIT_DURATION)
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
    return (
      <div className={`screen${exiting ? ' screen--exit' : ''}`}>
        <BranchSelection onSelect={handleBranchSelect} />
      </div>
    )
  }

  return (
    <div className="screen screen--enter">
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
    </div>
  )
}

export default App
