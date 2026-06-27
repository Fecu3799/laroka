function InstagramIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="2.5" width="19" height="19" rx="5.5" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="12" cy="12" r="4.2" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="17.3" cy="6.7" r="1.2" fill="currentColor" />
    </svg>
  )
}

function FacebookIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M14 8.5V6.8c0-.8.2-1.3 1.4-1.3H17V2.6C16.6 2.5 15.6 2.5 14.4 2.5c-2.4 0-4 1.5-4 4.2v1.8H8v3h2.4V21h3.6v-9.5h2.5l.4-3H14z"
        fill="currentColor"
      />
    </svg>
  )
}

function WhatsappIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 2.5a9.4 9.4 0 0 0-8 14.3L2.5 21.5l4.8-1.4A9.4 9.4 0 1 0 12 2.5zm0 1.8a7.6 7.6 0 0 1 6.4 11.7l-.2.3.7 2.6-2.7-.7-.3.2A7.6 7.6 0 1 1 12 4.3zm-3 3.4c-.2 0-.5.1-.7.4-.3.3-.9.9-.9 2.2s1 2.6 1.1 2.7c.1.2 1.9 3 4.7 4.1 2.3.9 2.8.7 3.3.7.5 0 1.6-.7 1.9-1.3.2-.7.2-1.2.2-1.3l-.5-.3c-.4-.2-1.6-.8-1.8-.9-.2-.1-.4-.1-.6.1-.2.3-.7.9-.8 1-.2.2-.3.2-.6.1-.4-.2-1.2-.5-2.3-1.4-.8-.7-1.4-1.6-1.5-1.9-.2-.3 0-.4.1-.6l.4-.5c.2-.2.2-.3.3-.5.1-.2.1-.4 0-.5l-.8-2c-.2-.5-.4-.4-.6-.4z"
        fill="currentColor"
      />
    </svg>
  )
}

function PinIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M12 21s-6-5.2-6-10a6 6 0 0 1 12 0c0 4.8-6 10-6 10z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <circle cx="12" cy="11" r="2.2" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 7.5V12l3 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function whatsappHref(value) {
  if (!value) return null
  if (/^https?:\/\//i.test(value)) return value
  const digits = value.replace(/[^\d]/g, '')
  return digits ? `https://wa.me/${digits}` : null
}

// JS getDay(): 0=Domingo .. 6=Sábado → clave WeekDay del backend.
const DAY_KEYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
const DAY_LABELS = {
  MON: 'Lunes', TUE: 'Martes', WED: 'Miércoles', THU: 'Jueves',
  FRI: 'Viernes', SAT: 'Sábado', SUN: 'Domingo',
}

// 'HH:mm' (o 'HH:mm:ss') → minutos desde medianoche; null si no hay valor.
function toMinutes(t) {
  if (!t) return null
  const [h, m] = t.split(':')
  const hh = Number(h), mm = Number(m)
  if (Number.isNaN(hh) || Number.isNaN(mm)) return null
  return hh * 60 + mm
}

function hhmm(t) {
  return t ? t.slice(0, 5) : ''
}

// Estado de atención del día actual (timezone local del navegador).
// Devuelve { open, text } o null si no hay schedule.
function getScheduleStatus(schedule) {
  if (!Array.isArray(schedule) || schedule.length === 0) return null

  const now = new Date()
  const todayKey = DAY_KEYS[now.getDay()]
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const byDay = {}
  schedule.forEach(d => { byDay[d.dayOfWeek] = d })

  const today = byDay[todayKey]
  if (today && today.active) {
    const hasSlots = today.openTime || today.closeTime || today.openTime2 || today.closeTime2
    if (!hasSlots) return { open: true, text: 'Abierto' }
    // Franja 1 por defecto; si ya pasó el cierre de la franja 1 y existe la 2, usá la 2.
    const close1 = toMinutes(today.closeTime)
    let closeStr = today.closeTime
    if (close1 != null && nowMin >= close1 && today.closeTime2) closeStr = today.closeTime2
    return closeStr
      ? { open: true, text: `Abierto · Cierra a las ${hhmm(closeStr)}` }
      : { open: true, text: 'Abierto' }
  }

  // Cerrado hoy: buscar el próximo día con active=true.
  for (let i = 1; i <= 7; i++) {
    const key = DAY_KEYS[(now.getDay() + i) % 7]
    const d = byDay[key]
    if (d && d.active) {
      return d.openTime
        ? { open: false, text: `Cerrado hoy · Abre el ${DAY_LABELS[key]} a las ${hhmm(d.openTime)}` }
        : { open: false, text: `Cerrado hoy · Abre el ${DAY_LABELS[key]}` }
    }
  }
  return { open: false, text: 'Cerrado' }
}

// Modal de presentación del negocio (US-13-F-02). Se muestra sobre el menú.
export function WelcomeModal({ profile, branch, onClose }) {
  const waHref = whatsappHref(profile.whatsapp)
  const hasSocial = profile.instagramUrl || profile.facebookUrl || waHref
  const status = getScheduleStatus(branch?.schedule)
  const hasBranchInfo = branch?.address || status

  return (
    <div
      className="welcome-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={`Sobre ${profile.businessName || 'nosotros'}`}
    >
      <div className="welcome-modal">
        {profile.logoUrl && (
          <img src={profile.logoUrl} alt="" className="welcome-logo" />
        )}

        {profile.businessName && (
          <h1 className="welcome-name">{profile.businessName}</h1>
        )}

        {profile.description && (
          <p className="welcome-description">{profile.description}</p>
        )}

        {hasBranchInfo && (
          <div className="welcome-branch">
            {branch?.address && (
              <p className="welcome-branch-row">
                <PinIcon />
                <span>{branch.address}</span>
              </p>
            )}
            {status && (
              <p className={`welcome-branch-row welcome-branch-hours welcome-branch-hours--${status.open ? 'open' : 'closed'}`}>
                <ClockIcon />
                <span>{status.text}</span>
              </p>
            )}
          </div>
        )}

        {hasSocial && (
          <div className="welcome-social">
            {profile.instagramUrl && (
              <a
                href={profile.instagramUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="welcome-social-link"
                aria-label="Instagram"
              >
                <InstagramIcon />
              </a>
            )}
            {profile.facebookUrl && (
              <a
                href={profile.facebookUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="welcome-social-link"
                aria-label="Facebook"
              >
                <FacebookIcon />
              </a>
            )}
            {waHref && (
              <a
                href={waHref}
                target="_blank"
                rel="noopener noreferrer"
                className="welcome-social-link"
                aria-label="WhatsApp"
              >
                <WhatsappIcon />
              </a>
            )}
          </div>
        )}

        <button type="button" className="welcome-cta" onClick={onClose}>
          Explorar el menú
        </button>
      </div>
    </div>
  )
}
