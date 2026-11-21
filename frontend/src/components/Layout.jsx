import { NavLink, Link, useNavigate } from 'react-router-dom'
import { useApp } from '../context.jsx'
import { usePrefs } from '../usePrefs.js'
import Icon from './Icon.jsx'

const ROLE_ORDER = ['EKSPERT', 'ADMIN']

const FONT_SIZES = [
  { scale: 0, sup: '', title: 'Tekst standardowy' },
  { scale: 1, sup: '+', title: 'Tekst większy' },
  { scale: 2, sup: '++', title: 'Tekst największy' },
]

/** Prawy blok paska: konto użytkownika. W trybie demo – szybkie przełączanie kont na prezentację. */
function AccountBlock() {
  const { people, user, chooseUser, logout, demoMode, meta, notify } = useApp()
  const navigate = useNavigate()
  const groups = ROLE_ORDER.map((role) => ({
    role,
    label: meta?.roles.find((r) => r.value === role)?.label ?? role,
    people: people.filter((p) => p.role.value === role),
  }))

  async function run(action) {
    try {
      await action()
    } catch (e) {
      notify(e.message, 'error')
    }
  }

  return (
    <div className="user-switcher">
      <Icon name="user" size={22} />
      {demoMode ? (
        <label title="Tryb demo: przełącz konto jednym kliknięciem">
          <span className="sr-only">Konto demo</span>
          <select value={user?.id ?? ''} onChange={(e) => run(() => chooseUser(e.target.value ? Number(e.target.value) : null))}>
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
      ) : (
        user && <span className="account-name">{user.name}</span>
      )}
      {user ? (
        <button type="button" className="strip-link" onClick={() => run(async () => {
          await logout()
          navigate('/')
        })}>
          Wyloguj
        </button>
      ) : (
        <Link to="/login" className="strip-link">Zaloguj się</Link>
      )}
    </div>
  )
}

export default function Layout({ children }) {
  const { user, error, toast } = useApp()
  const prefs = usePrefs()
  const isPci = user && ['EKSPERT', 'ADMIN'].includes(user.role.value)
  return (
    <div className="app">
      <div className="topstrip">
        <div className="topstrip-inner">
          <div className="a11y" aria-label="Ustawienia wyświetlania">
            <button type="button" className="a11y-btn" onClick={prefs.toggleTheme}
              aria-pressed={prefs.theme === 'dark'} title={prefs.theme === 'dark' ? 'Motyw jasny' : 'Motyw ciemny'}>
              <Icon name="contrast" size={24} />
            </button>
            <div className="font-sizes" role="group" aria-label="Wielkość tekstu">
              {FONT_SIZES.map((f) => (
                <button key={f.scale} type="button" className={`a11y-btn font-btn font-btn-${f.scale}`}
                  aria-pressed={prefs.fontScale === f.scale} onClick={() => prefs.setFontScale(f.scale)} title={f.title}>
                  A{f.sup && <sup>{f.sup}</sup>}
                </button>
              ))}
            </div>
          </div>
          <AccountBlock />
        </div>
      </div>
      <header className="topbar">
        <div className="wrap topbar-inner">
          <Link to="/" className="brand">
            <img src="/logo-black.png" alt="" className="brand-logo logo-on-light" />
            <img src="/logo-white.png" alt="" className="brand-logo logo-on-dark" />
            <span className="brand-text">
              <strong>TrackIdea</strong>
              <span>Radar innowacji</span>
            </span>
          </Link>
          <nav className="nav">
            <NavLink to="/" end>Katalog</NavLink>
            <NavLink to="/needs">Szukam rozwiązania</NavLink>
            <NavLink to="/pci">{isPci ? 'Panel PCI' : 'Radar'}</NavLink>
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
              <Link to="/needs">Szukam rozwiązania</Link>
              <Link to="/pci">Radar i mierniki</Link>
              <a href={`${import.meta.env.VITE_API_URL ?? ''}/swagger-ui.html`} target="_blank" rel="noreferrer">API</a>
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
