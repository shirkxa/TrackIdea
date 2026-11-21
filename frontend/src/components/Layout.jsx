import { NavLink, Link } from 'react-router-dom'
import { useApp } from '../context.jsx'

const ROLE_ORDER = ['TWORCA', 'OPIEKUN', 'EKSPERT', 'PARTNER', 'ADMIN']

function UserSwitcher() {
  const { people, user, chooseUser, meta } = useApp()
  const groups = ROLE_ORDER.map((role) => ({
    role,
    label: meta?.roles.find((r) => r.value === role)?.label ?? role,
    people: people.filter((p) => p.role.value === role),
  }))
  return (
    <label className="user-switcher" title="Przełącznik ról demo – w wersji produkcyjnej logowanie">
      <span className="sr-only">Użytkownik demo</span>
      <select value={user?.id ?? ''} onChange={(e) => chooseUser(e.target.value ? Number(e.target.value) : null)}>
        <option value="">Gość</option>
        {groups.map((g) => (
          <optgroup key={g.role} label={g.label}>
            {g.people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} · {p.institution?.name ?? ''}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </label>
  )
}

export default function Layout({ children }) {
  const { user, error, toast } = useApp()
  const isPci = user && ['EKSPERT', 'ADMIN'].includes(user.role.value)
  return (
    <div className="app">
      <div className="topstrip">
        <div className="wrap topstrip-inner">
          <Link to="/" className="topstrip-link">Szukaj w katalogu</Link>
          <UserSwitcher />
        </div>
      </div>
      <header className="topbar">
        <div className="wrap topbar-inner">
          <Link to="/" className="brand">
            <img src="/logo-black.png" alt="" className="brand-logo" />
            <span className="brand-text">
              <strong>TrackIdea</strong>
              <span>Radar innowacji</span>
            </span>
          </Link>
          <nav className="nav">
            <NavLink to="/" end>Katalog</NavLink>
            <NavLink to="/submit">Zgłoś</NavLink>
            <NavLink to="/needs">Potrzeby</NavLink>
            <NavLink to="/pci">{isPci ? 'Panel PCI' : 'Radar'}</NavLink>
            {user && <NavLink to="/me">Moje</NavLink>}
          </nav>
        </div>
      </header>
      <main className="main wrap">
        {error ? <div className="error-box">{error}</div> : children}
      </main>
      <footer className="footer">
        <div className="footer-band">
          <div className="wrap footer-inner">
            <Link to="/" className="footer-brand">
              <img src="/logo-white.png" alt="" />
              <span>TrackIdea</span>
            </Link>
            <nav className="footer-nav">
              <Link to="/">Katalog</Link>
              <Link to="/submit">Zgłoś innowację</Link>
              <Link to="/needs">Tablica potrzeb</Link>
              <Link to="/pci">Radar i mierniki</Link>
            </nav>
          </div>
        </div>
        <div className="wrap footer-bottom">
          © TrackIdea 2026 · HackNation, wyzwanie PCI „Radar innowacji” · dane demo są fikcyjne
        </div>
      </footer>
      {toast && <div className={`toast toast-${toast.kind}`}>{toast.message}</div>}
    </div>
  )
}
