import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

// Sube una imagen a R2 (US-15-01) bajo la subcarpeta del contexto (US-R2-01) y
// retorna la URL pública resultante.
// No se setea Content-Type: el browser arma el multipart/form-data con su boundary.
export async function uploadImage(file, token, context) {
  const formData = new FormData()
  formData.append('file', file)
  if (context) formData.append('context', context)
  const res = await apiFetch(`${API_URL}/backoffice/media/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  const data = await res.json()
  return data.url
}

// Lista las imágenes ya subidas por el tenant en la subcarpeta del contexto
// (US-R2-01). Retorna un array de { url, originalName, uploadedAt }.
export async function fetchMedia(context, token) {
  const res = await apiFetch(
    `${API_URL}/backoffice/media?context=${encodeURIComponent(context)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  return res.json()
}
