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
      <span className="avatar">{user ? user.name.split(' ').filter((w) => !w.endsWith('.')).map((w) => w[0]).slice(0, 2).join('') : '?'}</span>
      <select value={user?.id ?? ''} onChange={(e) => chooseUser(e.target.value ? Number(e.target.value) : null)}>
        <option value="">Gość (niezalogowany)</option>
        {groups.map((g) => (
          <optgroup key={g.role} label={g.label}>
            {g.people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} – {p.institution?.name ?? ''}
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
      <header className="topbar">
        <Link to="/" className="brand">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          <span>TrackIdea</span>
        </Link>
        <nav className="nav">
          <NavLink to="/" end>Katalog</NavLink>
          <NavLink to="/submit">Zgłoś innowację</NavLink>
          <NavLink to="/needs">Tablica potrzeb</NavLink>
          <NavLink to="/pci">{isPci ? 'Panel PCI' : 'Radar i mierniki'}</NavLink>
          {user && <NavLink to="/me">Moje</NavLink>}
        </nav>
        <UserSwitcher />
      </header>
      {user && (
        <div className="rolebar">
          Działasz jako <strong>{user.name}</strong> · {user.role.label}
          {user.institution && <> · {user.institution.name}</>}
        </div>
      )}
      <main className="main">
        {error ? <div className="error-box">{error}</div> : children}
      </main>
      <footer className="footer">
        TrackIdea · prototyp na HackNation, wyzwanie PCI „Radar innowacji”. Wszystkie instytucje, osoby i dane w demo są fikcyjne.
      </footer>
      {toast && <div className={`toast toast-${toast.kind}`}>{toast.message}</div>}
    </div>
  )
}
