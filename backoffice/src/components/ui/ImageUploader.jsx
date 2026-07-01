import { useRef, useState, useEffect } from 'react'
import { uploadImage } from '../../services/mediaService'
import './ImageUploader.css'

const ACCEPTED = 'image/jpeg,image/png,image/webp'

/**
 * Uploader de imagen reutilizable (US-15-F-02 / US-15-F-03).
 *
 * - Muestra la imagen actual (`value`) como preview; si no hay, un placeholder.
 * - Al elegir un archivo: preview local inmediato via URL.createObjectURL y upload
 *   automático a POST /backoffice/media/upload.
 * - En éxito llama onChange(url) con la URL pública devuelta por el backend. El
 *   consumidor persiste esa URL en su formulario (invisible para el usuario, sin
 *   campo de texto editable).
 * - En error muestra el mensaje inline, descarta el preview fallido y vuelve a la
 *   imagen previa, permitiendo reintentar sin tocar el resto del formulario.
 */
export default function ImageUploader({ value, onChange, token, label }) {
  const inputRef = useRef(null)
  const [localPreview, setLocalPreview] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)

  // Revoca el objectURL al desmontar o cuando se reemplaza por otro (cleanup del
  // efecto previo). Evita fugas de memoria de los blobs de preview.
  useEffect(() => {
    return () => { if (localPreview) URL.revokeObjectURL(localPreview) }
  }, [localPreview])

  const preview = localPreview ?? value ?? null

  function openPicker() {
    setError(null)
    inputRef.current?.click()
  }

  async function handleFile(e) {
    const file = e.target.files?.[0]
    // Reset del input para permitir re-seleccionar el mismo archivo tras un error.
    e.target.value = ''
    if (!file) return
    setError(null)
    setLocalPreview(URL.createObjectURL(file))
    setUploading(true)
    try {
      const uploadedUrl = await uploadImage(file, token)
      onChange(uploadedUrl)
    } catch (err) {
      // apiFetch ya emitió un toast con el mensaje del backend; lo repetimos inline.
      setError(err?.message ?? 'No se pudo subir la imagen.')
      setLocalPreview(null)   // descarta el preview fallido → vuelve a `value`
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="iu">
      {label && <span className="iu-label">{label}</span>}
      <div className="iu-body">
        <div className={`iu-preview${preview ? '' : ' iu-preview--empty'}`}>
          {preview
            ? <img src={preview} alt="Vista previa de la imagen" className="iu-img" />
            : <span className="iu-placeholder">Sin imagen</span>}
          {uploading && (
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

      {error && <p className="iu-error">{error}</p>}
    </div>
  )
}
