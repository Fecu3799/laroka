import { useEffect } from 'react'
import { LaRokaLogo } from './LaRokaLogo'

export function SplashScreen({ onComplete }) {
  useEffect(() => {
    const timer = setTimeout(onComplete, 2000)
    return () => clearTimeout(timer)
  }, [onComplete])

  return (
    <div className="splash-screen" role="status" aria-label="Cargando La Roka">
      <LaRokaLogo className="splash-logo" />
    </div>
  )
}
