import { useEffect } from 'react'

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"/>
    </svg>
  )
}

export function BranchDrawer({ isOpen, branches, onClose, onSelectBranch, currentBranchId }) {
  useEffect(() => {
    if (!isOpen) return
    const handleEscape = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [isOpen, onClose])

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) onClose()
  }

  const handleSelectBranch = (branchId) => {
    onSelectBranch(branchId)
    onClose()
  }

  return (
    <>
      {isOpen && (
        <div className="branch-drawer-backdrop" onClick={handleBackdropClick} aria-hidden="true" />
      )}
      <div className={`branch-drawer${isOpen ? ' branch-drawer--open' : ''}`}>
        <div className="branch-drawer-header">
          <h2 className="branch-drawer-title">Seleccionar sucursal</h2>
          <button
            className="branch-drawer-close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <CloseIcon />
          </button>
        </div>
        <ul className="branch-drawer-list" role="list">
          {branches.map(branch => (
            <li key={branch.id}>
              <button
                className={`branch-drawer-item${currentBranchId === branch.id ? ' branch-drawer-item--active' : ''}`}
                onClick={() => handleSelectBranch(branch.id)}
              >
                <span className="branch-drawer-item-name">{branch.name}</span>
                {currentBranchId === branch.id && (
                  <span className="branch-drawer-item-check" aria-hidden="true">✓</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}
