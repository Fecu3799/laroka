import { Component } from 'react'
import './ErrorBoundary.css'

/**
 * ErrorBoundary genérico para las pantallas principales del backoffice.
 *
 * React solo captura errores de render en un class component con
 * getDerivedStateFromError / componentDidCatch. Sin esto, un error de render en
 * cualquier página desmonta todo el árbol y deja la pantalla en negro sin ningún
 * mensaje. Acá mostramos un fallback legible y un botón para reintentar el
 * render (resetea el estado de error) sin recargar toda la app.
 *
 * `resetKey`: si cambia (ej. la ruta activa), el boundary se limpia solo para no
 * quedar "pegado" en el fallback al navegar a otra pantalla.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error, info) {
    // Log para diagnóstico; en producción lo captura la consola / monitoreo.
    console.error('ErrorBoundary capturó un error de render:', error, info)
  }

  componentDidUpdate(prevProps) {
    if (this.state.hasError && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false })
    }
  }

  handleRetry = () => {
    this.setState({ hasError: false })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary" role="alert">
          <div className="error-boundary-card">
            <h2 className="error-boundary-title">Algo salió mal</h2>
            <p className="error-boundary-message">
              No pudimos mostrar esta pantalla. Podés reintentar o recargar la página.
            </p>
            <div className="error-boundary-actions">
              <button
                type="button"
                className="error-boundary-btn error-boundary-btn--secondary"
                onClick={this.handleRetry}
              >
                Reintentar
              </button>
              <button
                type="button"
                className="error-boundary-btn error-boundary-btn--primary"
                onClick={() => window.location.reload()}
              >
                Recargar
              </button>
            </div>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
