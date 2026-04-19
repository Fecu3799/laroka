import { useState } from 'react'
import './Login.css'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  // TODO: In Sprint 2-F, use navigate to redirect after successful login

  const handleSubmit = (e) => {
    e.preventDefault()
    // TODO: Implement actual login logic in Sprint 2-F
    // This will call POST /auth/login and store JWT token
    console.log('Login attempt:', { email, password })
  }

  return (
    <div className="login-container">
      <div className="login-box">
        <h1>LaRoka Backoffice</h1>
        <p className="login-subtitle">Ingresá con tus credenciales</p>

        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tu@email.com"
              className="form-input"
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="form-input"
            />
          </div>

          <button type="submit" className="login-button">
            Ingresar
          </button>
        </form>

        <p className="login-note">
          Funcionalidad de login: disponible en Sprint 2-F
        </p>
      </div>
    </div>
  )
}
