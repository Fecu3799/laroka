import { useState, useEffect } from 'react'
import './OperatorStatusBar.css'

const TYPE_MOD = {
  info:    'osb--info',
  warning: 'osb--warning',
  danger:  'osb--danger',
}

export default function OperatorStatusBar({ messages, removeMessage }) {
  const [activeIdx, setActiveIdx] = useState(0)
  const [panelOpen, setPanelOpen] = useState(false)

  // Clamp activeIdx when messages shrink
  useEffect(() => {
    if (messages.length > 0 && activeIdx >= messages.length) {
      setActiveIdx(0)
    }
  }, [messages.length, activeIdx])

  // Rotate every 5 s when there are multiple messages
  useEffect(() => {
    if (messages.length <= 1) return
    const id = setInterval(() => {
      setActiveIdx(prev => (prev + 1) % messages.length)
    }, 5000)
    return () => clearInterval(id)
  }, [messages.length])

  // Close panel on Escape
  useEffect(() => {
    if (!panelOpen) return
    function onKey(e) { if (e.key === 'Escape') setPanelOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [panelOpen])

  if (messages.length === 0) return null

  const current = messages[activeIdx] ?? messages[0]
  const mod = TYPE_MOD[current.type] ?? 'osb--info'

  return (
    <>
      <div
        className={`osb-bar ${mod}`}
        role="button"
        tabIndex={0}
        onClick={() => setPanelOpen(true)}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') setPanelOpen(true) }}
        aria-label="Ver mensajes operativos"
      >
        <span className="osb-dot" />

        <span className="osb-text-wrapper">
          {/* key change triggers CSS enter animation */}
          <span key={current.id} className="osb-text">{current.text}</span>
        </span>

        {current.action && (
          <button
            className="osb-inline-action"
            type="button"
            onClick={e => { e.stopPropagation(); current.action.onClick() }}
          >
            {current.action.label}
          </button>
        )}

        {messages.length > 1 && (
          <span className="osb-more">+{messages.length - 1} más</span>
        )}
      </div>

      {panelOpen && (
        <div
          className="osb-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="Mensajes operativos"
          onClick={() => setPanelOpen(false)}
        >
          <div className="osb-panel" onClick={e => e.stopPropagation()}>
            <div className="osb-panel-header">
              <span className="osb-panel-title">Mensajes operativos</span>
              <button
                className="osb-panel-close"
                type="button"
                onClick={() => setPanelOpen(false)}
                aria-label="Cerrar"
              >
                ×
              </button>
            </div>

            <div className="osb-panel-list">
              {messages.map(msg => (
                <div
                  key={msg.id}
                  className={`osb-panel-item ${TYPE_MOD[msg.type] ?? 'osb--info'}`}
                >
                  <span className="osb-dot osb-dot--sm" />
                  <span className="osb-panel-item-text">{msg.text}</span>
                  <div className="osb-panel-item-actions">
                    {msg.action && (
                      <button
                        className="osb-panel-action-btn"
                        type="button"
                        onClick={() => { msg.action.onClick(); setPanelOpen(false) }}
                      >
                        {msg.action.label}
                      </button>
                    )}
                    {msg.type === 'danger' && (
                      <button
                        className="osb-panel-dismiss-btn"
                        type="button"
                        onClick={() => removeMessage(msg.id)}
                      >
                        Descartar
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  )
}
