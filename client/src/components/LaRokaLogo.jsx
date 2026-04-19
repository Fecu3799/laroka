import logoSrc from '../assets/logo.png'

export function LaRokaLogo({ className = '' }) {
  return (
    <img
      src={logoSrc}
      alt="La Roka Pizzería"
      className={`laroka-logo ${className}`.trim()}
    />
  )
}
