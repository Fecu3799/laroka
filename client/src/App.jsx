import { useState, useCallback, useRef, useEffect } from 'react'
import { SplashScreen } from './pages/SplashScreen'
import { BranchSelection } from './pages/BranchSelection'
import { ConfigError } from './pages/ConfigError'
import { MenuScreen } from './pages/MenuScreen'
import { PaymentResultScreen } from './pages/PaymentResultScreen'
import { PendingModal } from './features/payment/PaymentModals'
import { Toast } from './components/Toast'
import { usePreferredBranch } from './hooks/usePreferredBranch'
import { useTheme } from './hooks/useTheme'
import { isTenantConfigured } from './config'
import './App.css'

const SCREEN_EXIT_DURATION = 320
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

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
  const [pendingPaymentData, setPendingPaymentData] = useState(null)

  useEffect(() => () => clearTimeout(exitTimerRef.current), [])

  const handleSplashComplete = useCallback(async () => {
    const raw = sessionStorage.getItem('pedisur_checkout_recovery')
    if (raw && preferredBranchId) {
      let recovery
      try { recovery = JSON.parse(raw) } catch {
        sessionStorage.removeItem('pedisur_checkout_recovery')
      }
      if (recovery) {
        try {
          const r = await fetch(`${API_BASE}/orders/${recovery.orderId}/status`)
          const data = r.ok ? await r.json() : null
          if (data?.status === 'PENDING_PAYMENT') {
            setPendingPaymentData(recovery)
          } else {
            sessionStorage.removeItem('pedisur_checkout_recovery')
          }
        } catch {
          sessionStorage.removeItem('pedisur_checkout_recovery')
        }
      }
    }
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

  // Sin VITE_TENANT_ID no se puede resolver el tenant: mostramos el error de
  // configuración y no montamos ninguna pantalla que llame al backend.
  if (!isTenantConfigured) {
    return <ConfigError />
  }

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
          pendingPaymentRecovery={pendingPaymentData}
          onPendingPaymentConsumed={() => setPendingPaymentData(null)}
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
