import { useRef, useState, useEffect, useCallback } from 'react'
import ReactDOM from 'react-dom'
import Cropper from 'react-easy-crop'
import { uploadImage, fetchMedia } from '../../services/mediaService'
import './ImageUploader.css'

const ACCEPTED = 'image/jpeg,image/png,image/webp'
// El input filtra por `accept`, pero un drop lo esquiva: validamos el tipo a mano
// para dar feedback inmediato en vez de dejar que el backend rechace con 400.
const ACCEPTED_TYPES = new Set(ACCEPTED.split(','))

// Fallback de etiqueta cuando la miniatura no tiene originalName (imágenes
// subidas antes de US-R2-01): fecha de subida formateada.
function formatUploadedAt(iso) {
  if (!iso) return 'Imagen'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return 'Imagen'
  return date.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
// Tipos que preservan transparencia; si el original no es uno de estos, el recorte
// se emite como JPEG. Así un logo PNG/WebP no pierde su fondo transparente.
const TRANSPARENT_TYPES = new Set(['image/png', 'image/webp'])

// Traduce un error de upload a un mensaje específico para el usuario según la
// causa real. El backend ya devuelve mensajes concretos por tipo/formato inválido
// (400), tamaño excedido (400) o fallo de storage (502) en `err.message`; acá
// sólo mapeamos los sentinels internos de apiFetch (red/sesión) y damos un
// fallback final para no mostrar nunca un "Error" crudo.
function uploadErrorMessage(err) {
  if (err?.message === 'network_error') return 'Sin conexión. Verificá tu internet e intentá de nuevo.'
  if (err?.message === 'session_expired') return 'Tu sesión expiró. Volvé a iniciar sesión.'
  return err?.message ?? 'No se pudo subir la imagen. Intentá de nuevo.'
}

function createImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.addEventListener('load', () => resolve(img))
    img.addEventListener('error', () => reject(new Error('No se pudo leer la imagen.')))
    img.src = url
  })
}

/**
 * Recorta `imageSrc` a la región `pixelCrop` (coordenadas en px de la imagen
 * original, provistas por onCropComplete de react-easy-crop) y devuelve un Blob.
 */
async function getCroppedBlob(imageSrc, pixelCrop, mimeType) {
  const image = await createImage(imageSrc)
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(pixelCrop.width)
  canvas.height = Math.round(pixelCrop.height)
  const ctx = canvas.getContext('2d')
  ctx.drawImage(
    image,
    pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
    0, 0, canvas.width, canvas.height,
  )
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => (blob ? resolve(blob) : reject(new Error('No se pudo generar el recorte.'))),
      mimeType,
      0.92,
    )
  })
}

/**
 * Uploader de imagen reutilizable con recorte previo (US-15-F-02 / F-03 / F-06).
 *
 * - Muestra la imagen actual (`value`) como preview; si no hay, un placeholder.
 * - Al elegir un archivo NO sube nada: abre un editor de recorte con el marco fijado
 *   al `aspectRatio` recibido, donde el usuario mueve y hace zoom.
 * - "Confirmar" genera el recorte en un canvas y recién ahí sube el Blob a
 *   POST /backoffice/media/upload; en éxito llama onChange(url) con la URL pública.
 * - "Cancelar" descarta el recorte sin subir nada.
 * - En error muestra el mensaje inline y permite reintentar sin tocar el resto del
 *   formulario.
 *
 * Props:
 * - aspectRatio: número ancho/alto del marco de recorte. Si es null/omitido, se
 *   omite el editor y la imagen se sube tal cual (cualquier proporción), sin recorte.
 * - helperText: texto de ayuda opcional, visible antes de seleccionar el archivo.
 * - context: subcarpeta de R2 (products, branches, logo o bug-reports). Define
 *   dónde se sube/lista la imagen.
 * - enableGallery: si es true (default) y hay context, muestra el picker "Elegir
 *   de la galería" (US-R2-F-02). Se pasa false para contextos no listables como
 *   bug-reports, donde el backend no expone GET /media?context=bug-reports.
 */
export default function ImageUploader({ value, onChange, token, label, aspectRatio = null, helperText, context, enableGallery = true }) {
  const inputRef = useRef(null)
  const [localPreview, setLocalPreview] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)
  const [dragging, setDragging] = useState(false)

  // Estado del picker de galería (US-R2-F-02).
  const [galleryOpen, setGalleryOpen] = useState(false)
  const [galleryItems, setGalleryItems] = useState([])
  const [galleryLoading, setGalleryLoading] = useState(false)
  const [galleryError, setGalleryError] = useState(null)

  // Estado del editor de recorte.
  const [cropSrc, setCropSrc] = useState(null)     // objectURL del archivo original
  const [sourceMeta, setSourceMeta] = useState(null) // { name, type } del archivo
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null)

  // Revoca el objectURL del preview local al desmontar o al reemplazarlo.
  useEffect(() => {
    return () => { if (localPreview) URL.revokeObjectURL(localPreview) }
  }, [localPreview])

  // Revoca el objectURL del editor al desmontar o al reemplazarlo.
  useEffect(() => {
    return () => { if (cropSrc) URL.revokeObjectURL(cropSrc) }
  }, [cropSrc])

  const preview = localPreview ?? value ?? null
  const editing = cropSrc != null
  // Sólo se recorta cuando hay un aspectRatio válido; si no, se sube tal cual.
  const cropEnabled = typeof aspectRatio === 'number' && aspectRatio > 0

  const onCropComplete = useCallback((_area, areaPixels) => {
    setCroppedAreaPixels(areaPixels)
  }, [])

  function openPicker() {
    setError(null)
    inputRef.current?.click()
  }

  // Procesa un archivo elegido (por el selector o por drag & drop): si hay crop
  // habilitado abre el editor, si no sube directo. Misma lógica para ambos orígenes.
  function processFile(file) {
    if (!file) return
    setError(null)
    if (!cropEnabled) {
      uploadDirect(file) // sin recorte: se sube la imagen tal cual, cualquier proporción
      return
    }
    setCrop({ x: 0, y: 0 })
    setZoom(1)
    setCroppedAreaPixels(null)
    setSourceMeta({ name: file.name, type: file.type })
    setCropSrc(URL.createObjectURL(file)) // abre el editor; no sube todavía
  }

  function handleFile(e) {
    const file = e.target.files?.[0]
    // Reset del input para permitir re-seleccionar el mismo archivo tras cancelar.
    e.target.value = ''
    processFile(file)
  }

  // ── Drag & drop ──────────────────────────────────────────────
  function handleDragOver(e) {
    e.preventDefault() // necesario para que el navegador permita el drop
  }

  function handleDragEnter(e) {
    e.preventDefault()
    if (uploading) return
    setDragging(true)
  }

  function handleDragLeave(e) {
    e.preventDefault()
    // Ignora el leave hacia un hijo del propio dropzone: evita el parpadeo del borde.
    if (e.currentTarget.contains(e.relatedTarget)) return
    setDragging(false)
  }

  function handleDrop(e) {
    e.preventDefault()
    setDragging(false)
    if (uploading) return
    const file = e.dataTransfer?.files?.[0]
    if (!file) return
    if (!ACCEPTED_TYPES.has(file.type)) {
      setError('Formato no permitido. Subí una imagen JPG, PNG o WebP.')
      return
    }
    processFile(file)
  }

  async function uploadDirect(file) {
    setLocalPreview(URL.createObjectURL(file)) // preview local inmediato
    setUploading(true)
    try {
      const uploadedUrl = await uploadImage(file, token, context)
      onChange(uploadedUrl)
    } catch (err) {
      // apiFetch ya emitió un toast; repetimos inline el mensaje específico según la causa.
      setError(uploadErrorMessage(err))
      setLocalPreview(null) // descarta el preview fallido → vuelve a `value`
    } finally {
      setUploading(false)
    }
  }

  function closeEditor() {
    // Revoca vía el efecto de cleanup al setear cropSrc = null.
    setCropSrc(null)
    setSourceMeta(null)
    setCroppedAreaPixels(null)
  }

  function handleCancel() {
    if (uploading) return
    closeEditor()
  }

  async function handleConfirm() {
    if (!croppedAreaPixels || uploading) return
    setError(null)
    setUploading(true)
    try {
      const outType = TRANSPARENT_TYPES.has(sourceMeta.type) ? sourceMeta.type : 'image/jpeg'
      const blob = await getCroppedBlob(cropSrc, croppedAreaPixels, outType)
      const croppedFile = new File([blob], sourceMeta.name, { type: outType })
      const uploadedUrl = await uploadImage(croppedFile, token, context)
      // Preview local inmediato con el recorte ya generado.
      setLocalPreview(URL.createObjectURL(blob))
      onChange(uploadedUrl)
      closeEditor()
    } catch (err) {
      // apiFetch ya emitió un toast; repetimos inline el mensaje específico según la causa.
      setError(uploadErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  async function openGallery() {
    if (uploading) return
    setError(null)
    setGalleryError(null)
    setGalleryOpen(true)
    setGalleryLoading(true)
    try {
      const items = await fetchMedia(context, token)
      setGalleryItems(Array.isArray(items) ? items : [])
    } catch (err) {
      // apiFetch ya emitió un toast; lo repetimos inline dentro del picker.
      setGalleryError(err?.message ?? 'No se pudieron cargar las imágenes.')
      setGalleryItems([])
    } finally {
      setGalleryLoading(false)
    }
  }

  function closeGallery() {
    setGalleryOpen(false)
  }

  // Selecciona una imagen ya subida: mismo resultado que un upload exitoso, sin
  // pasar por el editor de recorte ni volver a subir nada.
  function selectFromGallery(url) {
    setError(null)
    setLocalPreview(null) // descarta cualquier preview local; manda `value`
    onChange(url)
    closeGallery()
  }

  const editor = editing && (
    <div className="iu-crop-backdrop" role="dialog" aria-modal="true" aria-label="Recortar imagen">
      <div className="iu-crop-modal" onClick={e => e.stopPropagation()}>
        <div className="iu-crop-area">
          <Cropper
            image={cropSrc}
            crop={crop}
            zoom={zoom}
            aspect={aspectRatio}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={onCropComplete}
          />
        </div>

        <div className="iu-crop-controls">
          <label className="iu-zoom-label" htmlFor="iu-zoom">Zoom</label>
          <input
            id="iu-zoom"
            className="iu-zoom"
            type="range"
            min={1}
            max={3}
            step={0.05}
            value={zoom}
            onChange={e => setZoom(Number(e.target.value))}
            disabled={uploading}
          />
        </div>

        {error && <p className="iu-error">{error}</p>}

        <div className="iu-crop-actions">
          <button type="button" className="iu-btn" onClick={handleCancel} disabled={uploading}>
            Cancelar
          </button>
          <button type="button" className="iu-btn iu-btn--primary" onClick={handleConfirm} disabled={uploading || !croppedAreaPixels}>
            {uploading ? 'Subiendo…' : 'Confirmar'}
          </button>
        </div>
      </div>
    </div>
  )

  const gallery = galleryOpen && (
    <div className="iu-gallery-backdrop" role="dialog" aria-modal="true" aria-label="Elegir de la galería" onClick={closeGallery}>
      <div className="iu-gallery-modal" onClick={e => e.stopPropagation()}>
        <div className="iu-gallery-header">
          <h3 className="iu-gallery-title">Elegir de la galería</h3>
          <button type="button" className="iu-gallery-close" onClick={closeGallery} aria-label="Cerrar">×</button>
        </div>

        {galleryLoading && (
          <div className="iu-gallery-status" aria-live="polite">
            <span className="iu-spinner" aria-hidden="true" />
            Cargando…
          </div>
        )}

        {!galleryLoading && galleryError && <p className="iu-error">{galleryError}</p>}

        {!galleryLoading && !galleryError && galleryItems.length === 0 && (
          <p className="iu-gallery-status">Todavía no hay imágenes en la galería.</p>
        )}

        {!galleryLoading && !galleryError && galleryItems.length > 0 && (
          <div className="iu-gallery-grid">
            {galleryItems.map(item => (
              <button
                type="button"
                key={item.url}
                className="iu-thumb"
                onClick={() => selectFromGallery(item.url)}
                title={item.originalName || formatUploadedAt(item.uploadedAt)}
              >
                <img src={item.url} alt={item.originalName || 'Imagen'} className="iu-thumb-img" />
                <span className="iu-thumb-label">
                  {item.originalName || formatUploadedAt(item.uploadedAt)}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )

  return (
    <div className="iu">
      {label && <span className="iu-label">{label}</span>}
      <div
        className={`iu-body${dragging ? ' iu-body--dragging' : ''}`}
        onDragOver={handleDragOver}
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {dragging && <div className="iu-drop-hint" aria-hidden="true">Soltá la imagen acá</div>}
        <div className={`iu-preview${preview ? '' : ' iu-preview--empty'}`}>
          {preview
            ? <img src={preview} alt="Vista previa de la imagen" className="iu-img" />
            : <span className="iu-placeholder">Sin imagen</span>}
          {uploading && !editing && (
            <div className="iu-overlay" aria-live="polite">
              <span className="iu-spinner" aria-hidden="true" />
              Subiendo…
            </div>
          )}
        </div>

        <div className="iu-actions">
          <button type="button" className="iu-btn" onClick={openPicker} disabled={uploading}>
            {preview ? 'Reemplazar imagen' : 'Subir imagen'}
          </button>
          {context && enableGallery && (
            <button type="button" className="iu-btn" onClick={openGallery} disabled={uploading}>
              Elegir de la galería
            </button>
          )}
        </div>

        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED}
          className="iu-file"
          onChange={handleFile}
          hidden
        />
      </div>

      {helperText && <p className="iu-helper">{helperText}</p>}

      {/* Errores fuera del editor (ej. si el editor ya se cerró). */}
      {error && !editing && <p className="iu-error">{error}</p>}

      {editor && ReactDOM.createPortal(editor, document.body)}
      {gallery && ReactDOM.createPortal(gallery, document.body)}
    </div>
  )
}
