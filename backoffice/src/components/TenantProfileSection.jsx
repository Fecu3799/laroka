import { useState, useEffect, useCallback, useRef } from 'react'
import useAuth from '../hooks/useAuth'
import { fetchTenantProfile, saveTenantProfile } from '../services/tenantService'

const EMPTY = {
  businessName: '',
  description: '',
  instagramUrl: '',
  facebookUrl: '',
  whatsapp: '',
  logoUrl: '',
}

function toForm(profile) {
  if (!profile) return EMPTY
  return {
    businessName: profile.businessName ?? '',
    description: profile.description ?? '',
    instagramUrl: profile.instagramUrl ?? '',
    facebookUrl: profile.facebookUrl ?? '',
    whatsapp: profile.whatsapp ?? '',
    logoUrl: profile.logoUrl ?? '',
  }
}

export default function TenantProfileSection() {
  const { token } = useAuth()

  const [form, setForm] = useState(EMPTY)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [status, setStatus] = useState('idle')   // idle | saving | saved | error
  const [saveError, setSaveError] = useState(null)
  const timerRef = useRef(null)

  const load = useCallback(() => {
    if (!token) return
    setLoading(true)
    setError(false)
    // 404 → null: formulario vacío listo para crear, no es error.
    fetchTenantProfile(token)
      .then(profile => setForm(toForm(profile)))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token])

  useEffect(() => { load() }, [load])

  useEffect(() => () => clearTimeout(timerRef.current), [])

  function handleChange(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
    setStatus('idle')
    setSaveError(null)
  }

  const canSave =
    form.businessName.trim() !== '' && form.description.trim() !== ''

  async function handleSave() {
    if (!canSave) return
    clearTimeout(timerRef.current)
    setStatus('saving')
    setSaveError(null)
    try {
      const payload = {
        businessName: form.businessName.trim(),
        description: form.description.trim(),
        instagramUrl: form.instagramUrl.trim() || null,
        facebookUrl: form.facebookUrl.trim() || null,
        whatsapp: form.whatsapp.trim() || null,
        logoUrl: form.logoUrl.trim() || null,
      }
      const saved = await saveTenantProfile(payload, token)
      setForm(toForm(saved))
      setStatus('saved')
      timerRef.current = setTimeout(() => setStatus('idle'), 2500)
    } catch (err) {
      setStatus('error')
      setSaveError(err?.message ?? 'No se pudo guardar.')
    }
  }

  const saving = status === 'saving'

  return (
    <section className="config-section">
      <div className="config-section-head">
        <div>
          <h2 className="config-section-title">Perfil del negocio</h2>
          <p className="config-section-sub">Datos que ve el cliente en la app</p>
        </div>
      </div>

      {loading ? (
        <div className="config-state-center"><div className="config-spinner" /></div>
      ) : error ? (
        <div className="config-state-center">
          <p className="config-state-error">No se pudo cargar el perfil.</p>
        </div>
      ) : (
        <div className="config-profile-form">
          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-business-name">
              Nombre del negocio <span className="config-profile-req">*</span>
            </label>
            <input
              id="tp-business-name"
              className="config-profile-input"
              type="text"
              value={form.businessName}
              onChange={e => handleChange('businessName', e.target.value)}
              placeholder="Ej: La Roka Pizzería"
            />
          </div>

          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-description">
              Descripción <span className="config-profile-req">*</span>
            </label>
            <textarea
              id="tp-description"
              className="config-profile-input config-profile-textarea"
              rows={4}
              value={form.description}
              onChange={e => handleChange('description', e.target.value)}
              placeholder="Contale al cliente sobre tu negocio"
            />
          </div>

          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-instagram">Instagram</label>
            <input
              id="tp-instagram"
              className="config-profile-input"
              type="text"
              value={form.instagramUrl}
              onChange={e => handleChange('instagramUrl', e.target.value)}
              placeholder="https://instagram.com/tunegocio"
            />
          </div>

          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-facebook">Facebook</label>
            <input
              id="tp-facebook"
              className="config-profile-input"
              type="text"
              value={form.facebookUrl}
              onChange={e => handleChange('facebookUrl', e.target.value)}
              placeholder="https://facebook.com/tunegocio"
            />
          </div>

          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-whatsapp">WhatsApp</label>
            <input
              id="tp-whatsapp"
              className="config-profile-input"
              type="text"
              value={form.whatsapp}
              onChange={e => handleChange('whatsapp', e.target.value)}
              placeholder="Ej: 5492804000000"
            />
          </div>

          <div className="config-profile-field">
            <label className="config-profile-label" htmlFor="tp-logo">Logo URL</label>
            <input
              id="tp-logo"
              className="config-profile-input"
              type="text"
              value={form.logoUrl}
              onChange={e => handleChange('logoUrl', e.target.value)}
              placeholder="https://…/logo.png"
            />
          </div>

          <div className="config-profile-actions">
            <button
              className="config-branch-save"
              onClick={handleSave}
              disabled={!canSave || saving}
            >
              {saving ? 'Guardando…' : 'Guardar'}
            </button>
            <span className="config-branch-feedback">
              {status === 'saved'
                ? <span className="config-feedback-ok">✓ Guardado</span>
                : status === 'error'
                  ? <span className="config-feedback-err">{saveError}</span>
                  : null}
            </span>
          </div>
        </div>
      )}
    </section>
  )
}
