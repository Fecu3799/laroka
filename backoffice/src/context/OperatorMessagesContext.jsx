import { createContext, useContext, useCallback, useState } from 'react'

const OperatorMessagesContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useOperatorMessagesContext() {
  const ctx = useContext(OperatorMessagesContext)
  if (!ctx) throw new Error('useOperatorMessagesContext debe usarse dentro de <OperatorMessagesProvider>')
  return ctx
}

export function OperatorMessagesProvider({ children }) {
  const [extraMessages, setExtraMessages] = useState([])

  const addMessage = useCallback((msg) => {
    setExtraMessages(prev => {
      if (prev.some(m => m.type === msg.type && m.text === msg.text)) return prev
      const id = `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`
      return [...prev, { ...msg, id }]
    })
  }, [])

  const removeMessage = useCallback((id) => {
    setExtraMessages(prev => prev.filter(m => m.id !== id))
  }, [])

  return (
    <OperatorMessagesContext.Provider value={{ extraMessages, addMessage, removeMessage }}>
      {children}
    </OperatorMessagesContext.Provider>
  )
}
