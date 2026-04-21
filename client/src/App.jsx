import { useState, useCallback, useRef, useEffect } from 'react'
import { SplashScreen } from './components/SplashScreen'
import { BranchSelection } from './components/BranchSelection'
import { MenuScreen } from './components/MenuScreen'
import { usePreferredBranch } from './hooks/usePreferredBranch'
import { useTheme } from './hooks/useTheme'
import './App.css'

const SCREEN_EXIT_DURATION = 320

function App() {
  const { preferredBranchId, preferredBranchName, saveBranch, clearBranch } = usePreferredBranch()
  useTheme()
  const [screen, setScreen] = useState('splash')
  const [selectedBranchId, setSelectedBranchId] = useState(preferredBranchId)
  const [selectedBranchName, setSelectedBranchName] = useState(preferredBranchName)
  const [exiting, setExiting] = useState(false)
  const exitTimerRef = useRef(null)

  useEffect(() => () => clearTimeout(exitTimerRef.current), [])

  const handleSplashComplete = useCallback(() => {
    setScreen(preferredBranchId ? 'menu' : 'selection')
  }, [preferredBranchId])

  const handleBranchSelect = useCallback((branchId, branchName) => {
    saveBranch(branchId, branchName)
    setSelectedBranchId(branchId)
    setSelectedBranchName(branchName)
    setExiting(true)
    exitTimerRef.current = setTimeout(() => {
      setScreen('menu')
      setExiting(false)
    }, SCREEN_EXIT_DURATION)
  }, [saveBranch])

  const handleChangeBranch = useCallback(() => {
    clearBranch()
    setSelectedBranchId(null)
    setSelectedBranchName(null)
    setScreen('selection')
  }, [clearBranch])

  const handleSwitchBranch = useCallback((newBranchId, newBranchName) => {
    saveBranch(newBranchId, newBranchName)
    setSelectedBranchId(newBranchId)
    setSelectedBranchName(newBranchName)
  }, [saveBranch])

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
      <MenuScreen
        branchId={selectedBranchId}
        branchName={selectedBranchName}
        onChangeBranch={handleChangeBranch}
        onSwitchBranch={handleSwitchBranch}
      />
    </div>
  )
}

export default App
