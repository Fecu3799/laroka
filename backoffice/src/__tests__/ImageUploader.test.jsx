import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import ImageUploader from '../components/ui/ImageUploader'
import { uploadImage, fetchMedia } from '../services/mediaService'

vi.mock('../services/mediaService', () => ({
  uploadImage: vi.fn(),
  fetchMedia: vi.fn(),
}))

// jsdom no implementa la API de object URLs que usa el preview local.
beforeEach(() => {
  vi.clearAllMocks()
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:local-preview')
  globalThis.URL.revokeObjectURL = vi.fn()
})

const GALLERY = [
  { url: 'https://cdn.r2.dev/7/products/a.png', originalName: 'muzza.png', uploadedAt: '2026-06-01T10:00:00Z' },
  { url: 'https://cdn.r2.dev/7/products/b.png', originalName: null, uploadedAt: '2026-06-02T12:30:00Z' },
]

describe('ImageUploader · picker de galería (US-R2-F-02)', () => {
  test('la galería se puebla con los datos del endpoint por contexto', async () => {
    fetchMedia.mockResolvedValue(GALLERY)

    render(<ImageUploader value={null} onChange={vi.fn()} token="tok" context="products" />)

    fireEvent.click(screen.getByText('Elegir de la galería'))

    // Se consulta el endpoint filtrando por el context recibido.
    await waitFor(() => expect(fetchMedia).toHaveBeenCalledWith('products', 'tok'))

    // La miniatura con originalName lo muestra; la que no lo tiene cae al uploadedAt formateado.
    expect(await screen.findByText('muzza.png')).toBeInTheDocument()
    expect(screen.getByText(/2026/)).toBeInTheDocument()
  })

  test('seleccionar una miniatura llama onChange con la URL, sin disparar upload', async () => {
    fetchMedia.mockResolvedValue(GALLERY)
    const onChange = vi.fn()

    render(<ImageUploader value={null} onChange={onChange} token="tok" context="products" />)

    fireEvent.click(screen.getByText('Elegir de la galería'))
    const thumb = await screen.findByText('muzza.png')

    fireEvent.click(thumb)

    expect(onChange).toHaveBeenCalledWith('https://cdn.r2.dev/7/products/a.png')
    // No se vuelve a subir nada al elegir una imagen ya existente.
    expect(uploadImage).not.toHaveBeenCalled()
    // El picker se cierra tras seleccionar.
    await waitFor(() => expect(screen.queryByText('Elegir de la galería')).toBeInTheDocument())
    expect(screen.queryByRole('dialog', { name: 'Elegir de la galería' })).not.toBeInTheDocument()
  })

  test('el flujo de subida nueva sigue intacto y envía el context', async () => {
    uploadImage.mockResolvedValue('https://cdn.r2.dev/7/products/new.png')
    const onChange = vi.fn()

    // Sin aspectRatio: la subida es directa (sin editor de recorte), igual que hoy.
    const { container } = render(
      <ImageUploader value={null} onChange={onChange} token="tok" context="products" />,
    )

    const file = new File([new Uint8Array([1, 2, 3])], 'nueva.png', { type: 'image/png' })
    const input = container.querySelector('input[type="file"]')
    fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith('https://cdn.r2.dev/7/products/new.png'),
    )
    expect(uploadImage).toHaveBeenCalledWith(file, 'tok', 'products')
    expect(fetchMedia).not.toHaveBeenCalled()
  })
})

describe('ImageUploader · drag & drop (US-17)', () => {
  test('soltar un archivo lo sube con el mismo flujo, enviando el context', async () => {
    uploadImage.mockResolvedValue('https://cdn.r2.dev/bug-reports/x.png')
    const onChange = vi.fn()

    const { container } = render(
      <ImageUploader value={null} onChange={onChange} token="tok" context="bug-reports" enableGallery={false} />,
    )

    const zone = container.querySelector('.iu-body')
    const file = new File([new Uint8Array([1, 2, 3])], 'captura.png', { type: 'image/png' })
    fireEvent.drop(zone, { dataTransfer: { files: [file] } })

    await waitFor(() => expect(uploadImage).toHaveBeenCalledWith(file, 'tok', 'bug-reports'))
    expect(onChange).toHaveBeenCalledWith('https://cdn.r2.dev/bug-reports/x.png')
  })

  test('arrastrar sobre el área muestra el resaltado de la zona de drop', () => {
    const { container } = render(<ImageUploader value={null} onChange={vi.fn()} token="tok" />)

    const zone = container.querySelector('.iu-body')
    expect(zone.className).not.toContain('iu-body--dragging')

    fireEvent.dragEnter(zone)

    expect(zone.className).toContain('iu-body--dragging')
    expect(screen.getByText('Soltá la imagen acá')).toBeInTheDocument()
  })

  test('soltar un archivo que no es imagen no sube y avisa el formato', () => {
    const onChange = vi.fn()
    const { container } = render(
      <ImageUploader value={null} onChange={onChange} token="tok" context="bug-reports" enableGallery={false} />,
    )

    const zone = container.querySelector('.iu-body')
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' })
    fireEvent.drop(zone, { dataTransfer: { files: [file] } })

    expect(uploadImage).not.toHaveBeenCalled()
    expect(screen.getByText(/Formato no permitido/)).toBeInTheDocument()
  })
})
