import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// US-SEC-02: fuentes self-hosted (antes @import remoto a fonts.googleapis.com).
// Servidas same-origin para cumplir la CSP estricta del backoffice sin font-src
// externo. Pesos exactos que usaba el @import original.
import '@fontsource/barlow-condensed/600.css'
import '@fontsource/barlow-condensed/700.css'
import '@fontsource/barlow-condensed/800.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/700.css'
import '@fontsource/jetbrains-mono/800.css'
import './index.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
