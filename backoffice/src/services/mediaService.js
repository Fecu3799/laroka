import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

// Sube una imagen a R2 (US-15-01) y retorna la URL pública resultante.
// No se setea Content-Type: el browser arma el multipart/form-data con su boundary.
export async function uploadImage(file, token) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await apiFetch(`${API_URL}/backoffice/media/upload`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  const data = await res.json()
  return data.url
}
