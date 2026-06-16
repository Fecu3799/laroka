import { useEffect, useRef, useState } from 'react'
import { useShift } from '../context/ShiftContext'
import { useOperatorMessagesContext } from '../context/OperatorMessagesContext'

function formatHour(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-AR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export default function useOperatorMessages() {
  const { shift, acceptingOrders, toggleOrders } = useShift()
  const { extraMessages, addMessage, removeMessage } = useOperatorMessagesContext()

  const notAcceptingTs = useRef(null)
  const [minsNotAccepting, setMinsNotAccepting] = useState(0)

  useEffect(() => {
    if (!shift || acceptingOrders) {
      notAcceptingTs.current = null
      setMinsNotAccepting(0)
      return
    }
    if (notAcceptingTs.current === null) {
      notAcceptingTs.current = Date.now()
      setMinsNotAccepting(0)
    }
    const id = setInterval(() => {
      setMinsNotAccepting(Math.floor((Date.now() - notAcceptingTs.current) / 60_000))
    }, 60_000)
    return () => clearInterval(id)
  }, [shift, acceptingOrders])

  const messages = []

  if (shift) {
    messages.push({
      id: 'shift-info',
      type: 'info',
      text: `Mostrando pedidos del turno actual desde ${formatHour(shift.openedAt)}`,
    })
  }

  if (shift && !acceptingOrders && minsNotAccepting >= 15) {
    messages.push({
      id: 'not-accepting',
      type: 'warning',
      text: `El local lleva ${minsNotAccepting} minutos sin aceptar pedidos`,
      action: { label: 'Activar', onClick: toggleOrders },
    })
  }

  messages.push(...extraMessages)

  return { messages, addMessage, removeMessage }
}
