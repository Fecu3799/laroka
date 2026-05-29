import { useState, useCallback, useRef, useEffect } from 'react'
import { SplashScreen } from './components/SplashScreen'
import { BranchSelection } from './components/BranchSelection'
import { MenuScreen } from './components/MenuScreen'
import { PaymentResultScreen } from './components/PaymentResultScreen'
import { PendingModal } from './components/PaymentModals'
import { Toast } from './components/Toast'
import { usePreferredBranch } from './hooks/usePreferredBranch'
import { useTheme } from './hooks/useTheme'
import './App.css'

const SCREEN_EXIT_DURATION = 320

function App() {
  const { preferredBranchId, preferredBranchName, saveBranch, clearBranch } = usePreferredBranch()
  useTheme()
  const [screen, setScreen] = useState(() => {
    const { pathname, search } = window.location
    console.log('[App] init — pathname:', pathname, 'search:', search)
    if (pathname === '/payment/result') return 'paymentResult'
    // Fallback: Vite dev server may not preserve the pathname when falling back to index.html
    const status = new URLSearchParams(search).get('status')
    if (['approved', 'failure', 'pending'].includes(status)) return 'paymentResult'
    return 'splash'
  })
  const [selectedBranchId, setSelectedBranchId] = useState(preferredBranchId)
  const [selectedBranchName, setSelectedBranchName] = useState(preferredBranchName)
  const [exiting, setExiting] = useState(false)
  const exitTimerRef = useRef(null)
  const [paymentFailureRecovery, setPaymentFailureRecovery] = useState(null)
  const [paymentPendingModal, setPaymentPendingModal] = useState(false)

  useEffect(() => () => clearTimeout(exitTimerRef.current), [])

  const handleSplashComplete = useCallback(() => {
    setScreen(preferredBranchId ? 'menu' : 'selection')
  }, [preferredBranchId])

  const handleBranchSelect = useCallback(({ id, name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes }) => {
    saveBranch({ id, name, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes })
    setSelectedBranchId(id)
    setSelectedBranchName(name)
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

  const handleSwitchBranch = useCallback((branch) => {
    saveBranch(branch)
    setSelectedBranchId(branch.id)
    setSelectedBranchName(branch.name)
  }, [saveBranch])

  const handlePaymentResultComplete = useCallback((result) => {
    window.history.replaceState({}, '', '/')
    if (result?.type === 'failure') {
      setPaymentFailureRecovery(result.recovery || null)
    } else if (result?.type === 'pending') {
      setPaymentPendingModal(true)
    }
    setScreen('menu')
  }, [])

  const handlePaymentFailureConsumed = useCallback(() => {
    setPaymentFailureRecovery(null)
  }, [])

  if (screen === 'paymentResult') {
    return (
      <PaymentResultScreen
        branchId={selectedBranchId}
        onComplete={handlePaymentResultComplete}
      />
    )
  }

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
    <>
      <div className="screen screen--enter">
        <MenuScreen
          branchId={selectedBranchId}
          branchName={selectedBranchName}
          onChangeBranch={handleChangeBranch}
          onSwitchBranch={handleSwitchBranch}
          paymentFailureRecovery={paymentFailureRecovery}
          onPaymentFailureConsumed={handlePaymentFailureConsumed}
        />
        {paymentPendingModal && (
          <PendingModal onClose={() => setPaymentPendingModal(false)} />
        )}
      </div>
      <Toast />
    </>
  )
}

export default App
