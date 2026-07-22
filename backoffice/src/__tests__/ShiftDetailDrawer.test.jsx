import { render, screen, fireEvent, within } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import History from '../pages/History'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { useHistory } from '../context/HistoryContext'

vi.mock('../hooks/useAuth', () => ({ default: vi.fn() }))
vi.mock('../hooks/useBranch', () => ({ default: vi.fn() }))
vi.mock('../context/HistoryContext', () => ({ useHistory: vi.fn() }))
vi.mock('../services/shiftsService', () => ({ getShiftHistory: vi.fn() }))
vi.mock('../services/ticketService', () => ({ downloadShiftSummary: vi.fn() }))

const SHIFT_A = {
  shiftId: '11111111-2222-3333-4444-5555aaaa',
  openedAt: '2026-07-18T10:00:00',
  closedAt: '2026-07-18T18:30:00',
  openedBy: 'Ana Gómez',
  summary: {
    deliveredOrders: 12, cancelledOrders: 1, totalRevenue: 48000,
    averageTicket: 4000, cashRevenue: 20000, mpRevenue: 18000, qrRevenue: 10000,
  },
}

const SHIFT_B = {
  shiftId: '99999999-8888-7777-6666-5555bbbb',
  openedAt: '2026-07-19T09:00:00',
  closedAt: '2026-07-19T17:00:00',
  openedBy: 'Luis Paz',
  summary: {
    deliveredOrders: 7, cancelledOrders: 0, totalRevenue: 21000,
    averageTicket: 3000, cashRevenue: 21000, mpRevenue: 0, qrRevenue: 0,
  },
}

function panel() {
  return document.querySelector('.drawer-panel')
}

// El nombre del encargado aparece en la fila y también dentro del panel, así que
// las queries se acotan a la tabla o al panel según corresponda.
function rowFor(shift) {
  return within(document.querySelector('.history-table')).getByText(shift.openedBy).closest('tr')
}

describe('Historial de turnos — panel lateral de detalle', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 't', tenantName: 'LaRoka' })
    useBranch.mockReturnValue({ activeBranchName: 'Centro' })
    useHistory.mockReturnValue({
      activeBranchId: 1,
      putPage: vi.fn(),
      getPage: () => ({
        content: [SHIFT_A, SHIFT_B],
        page: { totalPages: 1, number: 0 },
      }),
    })
  })

  test('no hay panel hasta que se hace click en una fila', () => {
    render(<History />)
    expect(panel()).toBeNull()

    fireEvent.click(rowFor(SHIFT_A))

    expect(panel()).not.toBeNull()
    expect(screen.getByText('Turno #180726-10:00')).toBeInTheDocument()
  })

  test('click en otra fila actualiza el contenido sin desmontar el panel', () => {
    render(<History />)
    fireEvent.click(rowFor(SHIFT_A))

    const before = panel()
    expect(screen.getByText('Turno #180726-10:00')).toBeInTheDocument()

    fireEvent.click(rowFor(SHIFT_B))

    // Mismo nodo del DOM ⇒ no hubo cierre + reapertura (ni reanimación de entrada).
    expect(panel()).toBe(before)
    expect(screen.getByText('Turno #190726-09:00')).toBeInTheDocument()
    expect(screen.queryByText('Turno #180726-10:00')).not.toBeInTheDocument()
    // El contenido del panel corresponde al turno B.
    expect(within(panel()).getByText('Luis Paz')).toBeInTheDocument()
  })

  test('la fila del turno abierto queda resaltada y la anterior deja de estarlo', () => {
    render(<History />)
    expect(rowFor(SHIFT_A).className).not.toContain('is-selected')

    fireEvent.click(rowFor(SHIFT_A))
    expect(rowFor(SHIFT_A).className).toContain('is-selected')
    expect(rowFor(SHIFT_B).className).not.toContain('is-selected')

    fireEvent.click(rowFor(SHIFT_B))
    expect(rowFor(SHIFT_A).className).not.toContain('is-selected')
    expect(rowFor(SHIFT_B).className).toContain('is-selected')
  })

  test('cerrar el panel quita el resaltado de la fila', () => {
    render(<History />)
    fireEvent.click(rowFor(SHIFT_A))

    fireEvent.click(screen.getByLabelText('Cerrar'))

    expect(panel()).toBeNull()
    expect(rowFor(SHIFT_A).className).not.toContain('is-selected')
  })

  test('Escape cierra el panel', () => {
    render(<History />)
    fireEvent.click(rowFor(SHIFT_A))

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(panel()).toBeNull()
  })

  test('el detalle muestra las métricas y el desglose del turno', () => {
    render(<History />)
    fireEvent.click(rowFor(SHIFT_A))

    expect(screen.getByText('Pedidos entregados')).toBeInTheDocument()
    expect(screen.getByText('Pedidos cancelados')).toBeInTheDocument()
    expect(screen.getByText('Ticket promedio')).toBeInTheDocument()
    expect(screen.getByText('Desglose de ingresos')).toBeInTheDocument()
    expect(screen.getByText('Efectivo')).toBeInTheDocument()
    expect(screen.getByText('MercadoPago')).toBeInTheDocument()
    expect(screen.getByText('QR')).toBeInTheDocument()
  })
})
