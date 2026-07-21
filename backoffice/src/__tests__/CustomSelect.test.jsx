import { render, screen, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import CustomSelect from '../components/CustomSelect'

// 20 opciones: el caso real que motivó el fix (20 categorías cargadas).
const MANY = Array.from({ length: 20 }, (_, i) => ({
  value: String(i + 1),
  label: `Categoría ${i + 1}`,
}))

function renderSelect(props = {}) {
  const onChange = vi.fn()
  render(
    <CustomSelect
      id="cs-test"
      value=""
      onChange={onChange}
      options={MANY}
      ariaLabelledBy="cs-label"
      {...props}
    />,
  )
  return { onChange }
}

const trigger = () => screen.getByRole('button', { expanded: false })
const list = () => document.querySelector('.cs-list')

// jsdom no hace layout: getBoundingClientRect devuelve todo en 0. Se stubea para simular un
// trigger ubicado en la pantalla y poder verificar el cálculo de posición.
function stubTriggerRect({ top, bottom, left = 40, width = 300 }) {
  const spy = vi
    .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
    .mockReturnValue({ top, bottom, left, width, right: left + width, height: bottom - top })
  return spy
}

beforeEach(() => {
  window.innerHeight = 800
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('CustomSelect · lista desplegable', () => {
  test('la lista no se renderiza hasta abrir', () => {
    renderSelect()

    expect(list()).toBeNull()
  })

  test('se renderiza fuera del formulario, en un portal a body', () => {
    // Es lo que evita que el overflow-y de los drawers la recorte.
    stubTriggerRect({ top: 100, bottom: 140 })
    const { container } = render(
      <div style={{ overflow: 'auto' }}>
        <CustomSelect id="x" value="" onChange={vi.fn()} options={MANY} />
      </div>,
    )
    fireEvent.click(screen.getAllByRole('button')[0])

    expect(document.querySelector('.cs-list')).toBeInTheDocument()
    expect(container.querySelector('.cs-list')).toBeNull()
  })

  test('acota el alto para que scrollee en vez de estirarse con las 20 opciones', () => {
    stubTriggerRect({ top: 100, bottom: 140 })
    renderSelect()
    fireEvent.click(trigger())

    expect(screen.getAllByRole('option')).toHaveLength(20)
    // Con 20 opciones la lista mide bastante más que esto: el tope la obliga a scrollear.
    expect(list().style.maxHeight).toBe('220px')
  })

  test('se abre hacia abajo cuando hay lugar', () => {
    stubTriggerRect({ top: 100, bottom: 140 })
    renderSelect()
    fireEvent.click(trigger())

    expect(list().style.top).toBe('146px')
    expect(list().style.bottom).toBe('')
  })

  test('se abre hacia arriba si abajo no entra', () => {
    // Trigger al pie de la ventana: abajo quedan 40px, arriba 740px.
    stubTriggerRect({ top: 720, bottom: 760 })
    renderSelect()
    fireEvent.click(trigger())

    expect(list().style.bottom).toBe('86px')
    expect(list().style.top).toBe('')
  })

  test('con poco espacio arriba y abajo, achica la lista en vez de salirse de pantalla', () => {
    window.innerHeight = 300
    stubTriggerRect({ top: 120, bottom: 160 })
    renderSelect()
    fireEvent.click(trigger())

    // Abajo quedan 300 - 160 - 12 = 128px, y es el lado con más lugar.
    expect(list().style.maxHeight).toBe('128px')
  })

  test('alinea la lista con el ancho y el borde izquierdo del trigger', () => {
    stubTriggerRect({ top: 100, bottom: 140, left: 64, width: 280 })
    renderSelect()
    fireEvent.click(trigger())

    expect(list().style.left).toBe('64px')
    expect(list().style.width).toBe('280px')
  })

  test('elegir una opción cierra la lista y avisa el valor', () => {
    stubTriggerRect({ top: 100, bottom: 140 })
    const { onChange } = renderSelect()
    fireEvent.click(trigger())

    fireEvent.click(screen.getByText('Categoría 7'))

    expect(onChange).toHaveBeenCalledWith('7')
    expect(list()).toBeNull()
  })

  test('Escape cierra la lista', () => {
    stubTriggerRect({ top: 100, bottom: 140 })
    renderSelect()
    fireEvent.click(trigger())

    fireEvent.keyDown(window, { key: 'Escape' })

    expect(list()).toBeNull()
  })
})
