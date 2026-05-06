import { useEffect, useRef } from 'react'

export function BranchDropdown({ isOpen, branches, onClose, onSelectBranch, currentBranchId, activeBranchIds = new Set() }) {
  const dropdownRef = useRef(null)

  useEffect(() => {
    if (!isOpen) return
    const handleEscape = (e) => {
      if (e.key === 'Escape') onClose()
    }
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        onClose()
      }
    }
    document.addEventListener('keydown', handleEscape)
    document.addEventListener('click', handleClickOutside)
    return () => {
      document.removeEventListener('keydown', handleEscape)
      document.removeEventListener('click', handleClickOutside)
    }
  }, [isOpen, onClose])

  const handleSelectBranch = (branchId) => {
    onSelectBranch(branchId)
    onClose()
  }

  return (
    <div
      className="branch-dropdown-container"
      ref={dropdownRef}
      onClick={(e) => e.stopPropagation()}
    >
      {isOpen && (
        <ul className="branch-dropdown-list" role="listbox">
          {branches.map(branch => (
            <li key={branch.id}>
              <button
                className={`branch-dropdown-item${currentBranchId === branch.id ? ' branch-dropdown-item--active' : ''}`}
                onClick={() => handleSelectBranch(branch.id)}
                role="option"
                aria-selected={currentBranchId === branch.id}
              >
                {activeBranchIds.has(branch.id) && (
                  <span className="branch-active-dot" aria-hidden="true" />
                )}
                {branch.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
