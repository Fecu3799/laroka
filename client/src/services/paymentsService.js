import { apiFetch } from './http'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const APP_URL = import.meta.env.VITE_APP_URL || 'http://localhost:5173'

export async function initiatePayment(orderId) {
  const backUrls = {
    success: `${APP_URL}/payment/result?status=approved&orderId=${orderId}`,
    failure: `${APP_URL}/payment/result?status=failure&orderId=${orderId}`,
    pending: `${APP_URL}/payment/result?status=pending&orderId=${orderId}`,
  }
  const res = await apiFetch(`${API_BASE}/payments/initiate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ orderId, backUrls }),
  })
  const data = await res.json()
  return data.paymentLink
}
