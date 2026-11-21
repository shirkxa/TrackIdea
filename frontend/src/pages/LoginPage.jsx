import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ErrorBox } from '../components/ui.jsx'
import { useApp } from '../context.jsx'

/** Przykładowe konta do szybkiego wypełnienia formularza w trybie demo. */
const DEMO_ACCOUNTS = [
  { email: 'k.nowak@pci.example.org', label: 'Katarzyna – analityk PCI' },
  { email: 'administrator.pci@pci.example.org', label: 'Administrator PCI' },
]

export default function LoginPage() {
  const { login, user, demoMode } = useApp()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email.trim(), password)
      navigate('/pci')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (user) {
    return (
      <section className="login card">
        <h1>Jesteś zalogowany(-a)</h1>
        <p>{user.name} · {user.role.label}</p>
        <Link className="btn btn-primary" to="/pci">Przejdź do panelu PCI</Link>
      </section>
    )
  }

  return (
    <section className="login card">
      <h1>Logowanie zespołu PCI</h1>
      <p className="muted small">Katalog jest publiczny – logowanie jest potrzebne tylko do weryfikacji i oceny.</p>
      <form onSubmit={submit} className="login-form">
        <ErrorBox>{error}</ErrorBox>
        <label className="field">
          <span>E-mail</span>
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>Hasło</span>
          <input type="password" autoComplete="current-password" required value={password}
            onChange={(e) => setPassword(e.target.value)} />
        </label>
        <button className="btn btn-primary" type="submit" disabled={busy}>{busy ? 'Logowanie…' : 'Zaloguj'}</button>
      </form>
      {demoMode && (
        <div className="login-demo">
          <p className="muted small">Konta demo (hasło: <code>demo123</code>):</p>
          <div className="actions">
            {DEMO_ACCOUNTS.map((a) => (
              <button key={a.email} type="button" className="chip"
                onClick={() => {
                  setEmail(a.email)
                  setPassword('demo123')
                }}>
                {a.label}
              </button>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
