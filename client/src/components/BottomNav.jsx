function PizzaIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M12 3L3 20h18L12 3z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round"/>
      <circle cx="12" cy="11" r="1.4" fill="currentColor"/>
      <circle cx="8.8" cy="15.5" r="1.1" fill="currentColor"/>
      <circle cx="15.2" cy="15.5" r="1.1" fill="currentColor"/>
    </svg>
  )
}

function CartIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round"/>
      <line x1="3" y1="6" x2="21" y2="6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/>
      <path d="M16 10a4 4 0 01-8 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/>
    </svg>
  )
}

function ProfileIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
      <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="1.6"/>
    </svg>
  )
}

const TABS = [
  { id: 'menu',    label: 'Menu',    Icon: PizzaIcon   },
  { id: 'cart',    label: 'Cart',    Icon: CartIcon    },
  { id: 'profile', label: 'Profile', Icon: ProfileIcon },
]

export function BottomNav({ activeTab, onTabChange, cartCount = 0 }) {
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {TABS.map(({ id, label, Icon }) => (
        <button
          key={id}
          className={`bottom-nav-item${activeTab === id ? ' bottom-nav-item--active' : ''}`}
          onClick={() => onTabChange(id)}
          aria-label={label}
          aria-current={activeTab === id ? 'page' : undefined}
        >
          {id === 'cart' ? (
            <div className="bottom-nav-cart-wrapper">
              <Icon />
              {cartCount > 0 && (
                <span className="bottom-nav-badge">
                  {cartCount > 99 ? '99+' : cartCount}
                </span>
              )}
            </div>
          ) : (
            <Icon />
          )}
          <span className="bottom-nav-label">{label}</span>
        </button>
      ))}
    </nav>
  )
}
