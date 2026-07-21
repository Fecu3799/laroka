import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import PRINT_CHILD_JS from '../../public/print-child.js?raw'
import { printTicket, printComanda, buildComandaModel, buildTicketModel } from '../services/ticketService'

/**
 * Regresión del bug de impresión (US-16B-04/05): la pestaña del backoffice se
 * congelaba mientras el diálogo nativo de impresión estaba abierto. Causa real
 * (confirmada en Chrome real con el diálogo nativo): la ventana hija compartía
 * el proceso de renderizado del opener, y el modal de impresión —modal a nivel
 * de proceso— bloqueaba ese proceso completo, sin importar quién llamara print().
 *
 * Fix: abrir la hija con `noopener` (proceso separado). Como noopener hace que
 * window.open() devuelva null, el contenido va por blob URL, y como la hija
 * hereda la CSP `script-src 'self'` (sin unsafe-inline), el disparo de print/
 * close vive en el script externo /print-child.js, no inline.
 *
 * Contrato que fijan estos tests:
 *  - openPrintWindow abre con blob URL + 'noopener' (aislamiento de proceso);
 *  - el HTML de la hija NO trae script inline (lo bloquearía la CSP) sino la
 *    referencia a /print-child.js;
 *  - la lógica de print/close/botón vive en public/print-child.js.
 */

// Captura el HTML que openPrintWindow mete en el Blob y las llamadas a
// window.open. Con noopener, window.open devuelve null (no hay handle).
function installCapture() {
  const openCalls = []
  let blob = null
  vi.spyOn(window, 'open').mockImplementation((...args) => { openCalls.push(args); return null })
  vi.spyOn(URL, 'createObjectURL').mockImplementation((b) => { blob = b; return 'blob:mock-url' })
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  return { openCalls, html: async () => (blob ? await blob.text() : '') }
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

describe('print-child.js — bootstrap de impresión de la ventana hija', () => {
  it('dispara print (onload) y cierra (onafterprint) desde la propia ventana hija', () => {
    expect(PRINT_CHILD_JS).toContain('window.onload')
    expect(PRINT_CHILD_JS).toContain('window.print()')
    expect(PRINT_CHILD_JS).toContain('window.onafterprint')
    expect(PRINT_CHILD_JS).toContain('window.close()')
  })
  it('cablea el botón "Cerrar" (#ticket-close) como fallback', () => {
    expect(PRINT_CHILD_JS).toContain('ticket-close')
    expect(PRINT_CHILD_JS).toContain('addEventListener')
  })
})

describe('printTicket — no bloquea la pestaña del backoffice', () => {
  let cap
  beforeEach(() => { cap = installCapture() })
  afterEach(() => vi.restoreAllMocks())

  it('abre la ventana hija con blob URL + noopener (proceso separado)', () => {
    printTicket(order, branch)
    expect(cap.openCalls).toHaveLength(1)
    expect(cap.openCalls[0]).toEqual(['blob:mock-url', '_blank', 'noopener'])
  })

  it('el HTML de la hija NO trae script inline (lo bloquearía la CSP)', async () => {
    printTicket(order, branch)
    const html = await cap.html()
    // sólo debe haber <script src=...>, nunca un <script> con código adentro
    expect(html).toContain('/print-child.js')
    expect(html).toMatch(/<script src="[^"]*\/print-child\.js"><\/script>/)
    expect(html).not.toContain('window.print()')
    expect(html).not.toContain('window.onafterprint')
  })

  it('incluye un botón "Cerrar" de fallback, oculto en la impresión', async () => {
    printTicket(order, branch)
    const html = await cap.html()
    expect(html).toContain('id="ticket-close"')
    expect(html).toContain('@media print')
    expect(html).toContain('.no-print')
  })

  it('no lanza cuando window.open devuelve null (normal con noopener)', () => {
    expect(() => printTicket(order, branch)).not.toThrow()
  })
})

describe('buildComandaModel — contenido mínimo de cocina', () => {
  it('expone número secuencial, tipo, hora, ítems (sin precio) y notas', () => {
    const model = buildComandaModel({
      orderNumber: 47, orderType: 'DELIVERY', createdAt: '2026-07-10T20:34:00',
      items: [{ quantity: 2, productName: 'Muzzarella', unitPrice: 8000 }],
      notes: 'Sin cebolla',
    })
    expect(model.orderNumber).toBe('Orden #47')
    expect(model.orderTypeLabel).toBe('Delivery')
    expect(model.receivedAt).toBe('20:34')
    expect(model.items).toEqual([{ quantity: 2, name: 'Muzzarella' }])
    expect(model.items[0]).not.toHaveProperty('unitPrice')
    expect(model.notes).toBe('Sin cebolla')
  })

  it('normaliza notas vacías/espacios a cadena vacía', () => {
    expect(buildComandaModel({ notes: '   ' }).notes).toBe('')
    expect(buildComandaModel({}).notes).toBe('')
  })

  it('usa el fallback de UUID si no hay orderNumber (pedido legado)', () => {
    expect(buildComandaModel({ id: 'e8a3cdcd-1234' }).orderNumber).toBe('#ORDER-e8a3cdcd')
  })
})

describe('printComanda — misma impresión no bloqueante que el ticket', () => {
  let cap
  beforeEach(() => { cap = installCapture() })
  afterEach(() => vi.restoreAllMocks())

  const comandaOrder = {
    id: 'aaaa-bbbb', orderNumber: 47, orderType: 'DELIVERY', createdAt: '2026-07-10T20:34:00',
    items: [{ quantity: 2, productName: 'Muzzarella', unitPrice: 8000 }],
    notes: 'Sin cebolla, cortada en 8',
    totalAmount: 16000, paymentMethod: 'CASH', customerName: 'Juan', deliveryAddress: 'Calle 9 1234',
  }

  it('abre la ventana hija con blob URL + noopener', () => {
    printComanda(comandaOrder)
    expect(cap.openCalls[0]).toEqual(['blob:mock-url', '_blank', 'noopener'])
  })

  it('referencia el script externo y NO trae script inline', async () => {
    printComanda(comandaOrder)
    const html = await cap.html()
    expect(html).toMatch(/<script src="[^"]*\/print-child\.js"><\/script>/)
    expect(html).not.toContain('window.print()')
    expect(html).toContain('id="ticket-close"')
  })

  it('muestra número, tipo, hora recepción, ítems y notas destacadas', async () => {
    printComanda(comandaOrder)
    const html = await cap.html()
    expect(html).toContain('Orden #47')
    expect(html).toContain('Delivery')       // se muestra en mayúsculas vía CSS text-transform
    expect(html).toContain('Recibido 20:34')
    expect(html).toContain('Muzzarella')
    expect(html).toContain('class="notes"')
    expect(html).toContain('Sin cebolla, cortada en 8')
  })

  it('NO incluye precios, total, ni datos de pago/dirección (es de cocina)', async () => {
    printComanda(comandaOrder)
    const html = await cap.html()
    expect(html).not.toContain('TOTAL')
    expect(html).not.toContain('8.000')       // precio de ítem formateado
    expect(html).not.toContain('16.000')      // total formateado
    expect(html).not.toContain('Efectivo')    // medio de pago
    expect(html).not.toContain('Calle 9 1234') // dirección de entrega
  })

  it('omite el recuadro de notas si el pedido no tiene notas', async () => {
    printComanda({ ...comandaOrder, notes: '' })
    expect(await cap.html()).not.toContain('class="notes"')
  })
})

describe('ítem mitad y mitad en ticket y comanda (US-HH-04)', () => {
  let cap
  beforeEach(() => { cap = installCapture() })
  afterEach(() => { vi.restoreAllMocks() })

  const COMBO = { quantity: 1, productName: 'Muzzarella', secondProductName: 'Calabresa', unitPrice: 3400 }
  const SIMPLE = { quantity: 2, productName: 'Fugazzeta', secondProductName: null, unitPrice: 2500 }

  it('buildTicketModel lista la combinación completa', () => {
    const model = buildTicketModel({ items: [COMBO, SIMPLE] }, {})

    expect(model.items[0].name).toBe('½ Muzzarella + ½ Calabresa')
    expect(model.items[1].name).toBe('Fugazzeta')
  })

  it('buildComandaModel lista la combinación completa', () => {
    // Es el caso crítico de la historia: en cocina, leer sólo la primera mitad
    // significa preparar la pizza equivocada.
    const model = buildComandaModel({ items: [COMBO, SIMPLE] })

    expect(model.items).toEqual([
      { quantity: 1, name: '½ Muzzarella + ½ Calabresa' },
      { quantity: 2, name: 'Fugazzeta' },
    ])
  })

  it('la comanda impresa muestra la combinación con la misma tipografía que el resto', async () => {
    printComanda({ orderNumber: 47, orderType: 'TAKEAWAY', createdAt: '2026-07-10T20:34:00', items: [COMBO] })
    const html = await cap.html()

    expect(html).toContain('½ Muzzarella + ½ Calabresa')
    // Sin clase propia ni tamaño reducido: usa item-name como cualquier otro ítem.
    expect(html).toContain('<span class="item-name">½ Muzzarella + ½ Calabresa</span>')
  })

  it('el ticket impreso muestra la combinación en la columna de detalle', async () => {
    printTicket({ orderNumber: 47, orderType: 'TAKEAWAY', createdAt: '2026-07-10T20:34:00', items: [COMBO] }, {})
    const html = await cap.html()

    expect(html).toContain('<td class="name">½ Muzzarella + ½ Calabresa</td>')
  })
})

describe('ítem con tamaño en ticket y comanda', () => {
  const CHICA = { quantity: 1, productName: 'Muzzarella', sizeName: 'CHICA', unitPrice: 9000 }
  const GRANDE = { quantity: 2, productName: 'Napolitana', sizeName: null, unitPrice: 17000 }

  it('buildTicketModel muestra el tamaño entre paréntesis', () => {
    const model = buildTicketModel({ items: [CHICA, GRANDE] }, {})

    expect(model.items[0].name).toBe('Muzzarella (Chica)')
    // El grande es implícito: sin sufijo, igual que antes de existir los tamaños.
    expect(model.items[1].name).toBe('Napolitana')
  })

  it('buildComandaModel muestra el tamaño, que en cocina cambia qué se prepara', () => {
    const model = buildComandaModel({ items: [CHICA, GRANDE] })

    expect(model.items).toEqual([
      { quantity: 1, name: 'Muzzarella (Chica)' },
      { quantity: 2, name: 'Napolitana' },
    ])
  })

  it('la comanda impresa muestra el tamaño con la misma tipografía que el resto', async () => {
    const cap = installCapture()
    printComanda({ orderNumber: 47, orderType: 'TAKEAWAY', createdAt: '2026-07-10T20:34:00', items: [CHICA] })
    const html = await cap.html()

    expect(html).toContain('<span class="item-name">Muzzarella (Chica)</span>')
    vi.restoreAllMocks()
  })

  it('el ticket impreso muestra el tamaño en la columna de detalle', async () => {
    const cap = installCapture()
    printTicket({ orderNumber: 47, orderType: 'TAKEAWAY', createdAt: '2026-07-10T20:34:00', items: [CHICA] }, {})
    const html = await cap.html()

    expect(html).toContain('<td class="name">Muzzarella (Chica)</td>')
    vi.restoreAllMocks()
  })
})
