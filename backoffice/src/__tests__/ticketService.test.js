import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { printTicket } from '../services/ticketService'

/**
 * Regresión del bug de impresión (US-16B-04): al imprimir, la pestaña del
 * backoffice quedaba congelada porque printTicket llamaba window.print() y
 * window.close() de forma SÍNCRONA sobre la ventana hija desde el hilo del
 * opener. window.print() bloquea hasta que se cierra el diálogo → el opener no
 * respondía.
 *
 * El fix mueve print/close al hilo de la ventana hija (window.onload /
 * window.onafterprint) más un botón "Cerrar" de fallback. Estos tests fijan ese
 * contrato: el opener NO debe tocar print()/close(), y el documento hijo debe
 * traer la lógica de auto-impresión/cierre.
 */

function makeFakeWindow() {
  let html = ''
  return {
    document: {
      open: vi.fn(),
      write: vi.fn((s) => { html += s }),
      close: vi.fn(),
    },
    focus: vi.fn(),
    print: vi.fn(),
    close: vi.fn(),
    getHtml: () => html,
  }
}

const order = {
  id: 'e8a3cdcd-1234-4abc-9def-0123456789ab',
  orderNumber: 47,
  orderType: 'TAKEAWAY',
  items: [{ quantity: 1, productName: 'Muzzarella', unitPrice: 8000 }],
  totalAmount: 8000,
  paymentMethod: 'CASH',
  createdAt: '2026-07-10T20:00:00',
}
const branch = { name: 'Centro', tenantName: 'LaRoka' }

describe('printTicket — no bloquea la pestaña del backoffice', () => {
  let fakeWin
  beforeEach(() => {
    fakeWin = makeFakeWindow()
    vi.spyOn(window, 'open').mockReturnValue(fakeWin)
  })
  afterEach(() => vi.restoreAllMocks())

  it('el opener NO llama a print() ni close() sobre la ventana hija', () => {
    printTicket(order, branch)
    expect(fakeWin.print).not.toHaveBeenCalled()
    expect(fakeWin.close).not.toHaveBeenCalled()
  })

  it('abre la ventana hija, le escribe el ticket y le da foco', () => {
    printTicket(order, branch)
    expect(window.open).toHaveBeenCalledWith('', '_blank')
    expect(fakeWin.document.write).toHaveBeenCalled()
    expect(fakeWin.focus).toHaveBeenCalled()
  })

  it('el documento hijo se auto-imprime (onload) y se auto-cierra (onafterprint)', () => {
    printTicket(order, branch)
    const html = fakeWin.getHtml()
    expect(html).toContain('window.onload')
    expect(html).toContain('window.print()')
    expect(html).toContain('window.onafterprint')
    expect(html).toContain('window.close()')
  })

  it('incluye un botón "Cerrar" de fallback, oculto en la impresión', () => {
    printTicket(order, branch)
    const html = fakeWin.getHtml()
    expect(html).toContain('id="ticket-close"')
    expect(html).toContain('@media print')
    expect(html).toContain('.no-print')
  })

  it('lanza si el navegador bloquea el pop-up (window.open devuelve null)', () => {
    window.open.mockReturnValue(null)
    expect(() => printTicket(order, branch)).toThrow(/pop-ups/)
  })
})
