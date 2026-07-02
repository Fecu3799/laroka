import { useState, useEffect, useRef } from 'react'
import useAuth from '../hooks/useAuth'
import { saveTenantProfile } from '../services/tenantService'
import { useConfig } from '../context/ConfigContext'
import ImageUploader from './ui/ImageUploader'

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
  // Perfil del tenant desde el catálogo global cacheado (US-14-F-05).
  const { tenantProfile, loadingConfig, reloadTenantProfile } = useConfig()

  const [form, setForm] = useState(EMPTY)
  const [status, setStatus] = useState('idle')   // idle | saving | saved | error
  const [saveError, setSaveError] = useState(null)
  const timerRef = useRef(null)

  // Seedea el formulario desde el perfil cacheado (404 → null → formulario vacío).
  useEffect(() => { setForm(toForm(tenantProfile)) }, [tenantProfile])

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
      // Refresca el perfil en el catálogo global.
      reloadTenantProfile()
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

      {loadingConfig ? (
        <div className="config-state-center"><div className="config-spinner" /></div>
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
            <ImageUploader
              label="Logo"
              value={form.logoUrl || null}
              onChange={url => handleChange('logoUrl', url)}
              token={token}
              helperText="Se admite cualquier proporción; el logo se muestra completo."
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
