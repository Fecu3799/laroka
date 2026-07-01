import { useRef, useState, useEffect, useCallback } from 'react'
import ReactDOM from 'react-dom'
import Cropper from 'react-easy-crop'
import { uploadImage } from '../../services/mediaService'
import './ImageUploader.css'

const ACCEPTED = 'image/jpeg,image/png,image/webp'
// Tipos que preservan transparencia; si el original no es uno de estos, el recorte
// se emite como JPEG. Así un logo PNG/WebP no pierde su fondo transparente.
const TRANSPARENT_TYPES = new Set(['image/png', 'image/webp'])

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
 */
export default function ImageUploader({ value, onChange, token, label, aspectRatio = null, helperText }) {
  const inputRef = useRef(null)
  const [localPreview, setLocalPreview] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)

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

  function handleFile(e) {
    const file = e.target.files?.[0]
    // Reset del input para permitir re-seleccionar el mismo archivo tras cancelar.
    e.target.value = ''
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

  async function uploadDirect(file) {
    setLocalPreview(URL.createObjectURL(file)) // preview local inmediato
    setUploading(true)
    try {
      const uploadedUrl = await uploadImage(file, token)
      onChange(uploadedUrl)
    } catch (err) {
      // apiFetch ya emitió un toast con el mensaje del backend; lo repetimos inline.
      setError(err?.message ?? 'No se pudo subir la imagen.')
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
      const uploadedUrl = await uploadImage(croppedFile, token)
      // Preview local inmediato con el recorte ya generado.
      setLocalPreview(URL.createObjectURL(blob))
      onChange(uploadedUrl)
      closeEditor()
    } catch (err) {
      // apiFetch ya emitió un toast con el mensaje del backend; lo repetimos inline.
      setError(err?.message ?? 'No se pudo subir la imagen.')
    } finally {
      setUploading(false)
    }
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

  return (
    <div className="iu">
      {label && <span className="iu-label">{label}</span>}
      <div className="iu-body">
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

        <button type="button" className="iu-btn" onClick={openPicker} disabled={uploading}>
          {preview ? 'Reemplazar imagen' : 'Subir imagen'}
        </button>

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
    </div>
  )
}
