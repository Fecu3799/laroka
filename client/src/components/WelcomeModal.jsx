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

function whatsappHref(value) {
  if (!value) return null
  if (/^https?:\/\//i.test(value)) return value
  const digits = value.replace(/[^\d]/g, '')
  return digits ? `https://wa.me/${digits}` : null
}

// Modal de presentación del negocio (US-13-F-02). Se muestra sobre el menú.
export function WelcomeModal({ profile, onClose }) {
  const waHref = whatsappHref(profile.whatsapp)
  const hasSocial = profile.instagramUrl || profile.facebookUrl || waHref

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
