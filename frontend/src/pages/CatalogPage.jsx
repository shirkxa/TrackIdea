import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../api.js'
import Icon from '../components/Icon.jsx'
import InnovationCard from '../components/InnovationCard.jsx'
import { Empty, ErrorBox, Loading, SelectEnum } from '../components/ui.jsx'
import { useApp } from '../context.jsx'

const TILES = [
  { to: '/submit', icon: 'idea', label: 'Zgłoś innowację' },
  { to: '/needs', icon: 'needs', label: 'Tablica potrzeb' },
  { to: '/pci', icon: 'radar', label: 'Radar' },
  { to: '/pci', icon: 'chart', label: 'Mierniki' },
  { to: '/me', icon: 'user', label: 'Moje' },
]

const SORTS = [
  { value: 'newest', label: 'Najnowsze' },
  { value: 'score', label: 'Najlepiej ocenione' },
  { value: 'popular', label: 'Najpopularniejsze' },
  { value: 'forks', label: 'Najczęściej wdrażane' },
]

const PUBLIC_INSTITUTIONS = ['SZKOLA', 'UCZELNIA', 'JST', 'INSTYTUCJA_PUBLICZNA']

export default function CatalogPage() {
  const { meta } = useApp()
  const [filters, setFilters] = useState({ sort: 'newest' })
  const [panelOpen, setPanelOpen] = useState(false)
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [cards, setCards] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 300)
    return () => clearTimeout(t)
  }, [q])

  useEffect(() => {
    api
      .get(`/innovations${query({ ...filters, q: debouncedQ })}`)
      .then((d) => {
        setCards(d)
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [filters, debouncedQ])

  const set = (key) => (value) => setFilters((f) => ({ ...f, [key]: value }))
  const statuses = meta.statuses.filter((s) => !s.draft && s.step > 0).map((s) => ({ value: s.value, label: s.label }))

  // Filtry w panelu „Filtry” – pokazywane potem jako usuwalne etykiety.
  const panelFilters = [
    { key: 'sector', label: 'Sektor', options: meta.sectors },
    { key: 'maturity', label: 'Etap', options: meta.maturities },
    { key: 'transferModel', label: 'Model transferu', options: meta.transferModels },
    { key: 'institutionType', label: 'Instytucja', options: meta.institutionTypes.filter((t) => PUBLIC_INSTITUTIONS.includes(t.value)) },
    { key: 'status', label: 'Status', options: statuses },
  ]
  const active = panelFilters
    .filter((f) => filters[f.key])
    .map((f) => ({ ...f, valueLabel: f.options.find((o) => o.value === filters[f.key])?.label }))
  const clearAll = () => setFilters((f) => ({ sort: f.sort, type: f.type }))
  const sortLabel = debouncedQ && filters.sort === 'newest' ? 'Najtrafniejsze' : null

  return (
    <>
      <h1 className="page-title">Katalog innowacji</h1>

      <nav className="tiles" aria-label="Na skróty">
        {TILES.map((t, i) => (
          <Link key={t.label} to={t.to} className="tile pop" style={{ '--i': i }}>
            <Icon name={t.icon} size={36} />
            <span>{t.label}</span>
          </Link>
        ))}
      </nav>

      <div className="toolbar">
        <label className="search">
          <Icon name="search" size={20} />
          <input
            type="search"
            placeholder="Szukaj problemu lub rozwiązania, np. kolejki w urzędzie"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Szukaj innowacji"
          />
        </label>
        <button type="button" className={`filter-toggle ${panelOpen || active.length ? 'on' : ''}`}
          onClick={() => setPanelOpen((o) => !o)} aria-expanded={panelOpen}>
          <Icon name="sliders" size={18} />
          Filtry{active.length > 0 && <span className="count">{active.length}</span>}
        </button>
      </div>

      {panelOpen && (
        <div className="filter-panel">
          {panelFilters.map((f) => (
            <SelectEnum key={f.key} label={f.label} value={filters[f.key]} onChange={set(f.key)} options={f.options} />
          ))}
        </div>
      )}

      <div className="list-bar">
        <div className="type-tabs" role="tablist" aria-label="Typ innowacji">
          <button type="button" role="tab" aria-selected={!filters.type} className={!filters.type ? 'on' : ''}
            onClick={() => set('type')(null)}>
            Wszystkie
          </button>
          {meta.types.map((t) => (
            <button key={t.value} type="button" role="tab" aria-selected={filters.type === t.value}
              className={filters.type === t.value ? 'on' : ''} onClick={() => set('type')(t.value)}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="results-bar">
        <span className="muted small">{cards ? `${cards.length} wyników` : ''}</span>
        <label className="sort">
          <span className="muted small">Sortuj:</span>
          <select value={filters.sort} onChange={(e) => set('sort')(e.target.value)} aria-label="Sortowanie">
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>{o.value === 'newest' && sortLabel ? sortLabel : o.label}</option>
            ))}
          </select>
        </label>
      </div>

      {active.length > 0 && (
        <div className="active-filters">
          {active.map((f) => (
            <button key={f.key} type="button" className="chip chip-on" onClick={() => set(f.key)(null)}
              title="Usuń filtr">
              {f.label}: {f.valueLabel} ✕
            </button>
          ))}
          <button type="button" className="link-button" onClick={clearAll}>Wyczyść</button>
        </div>
      )}

      <ErrorBox>{error}</ErrorBox>
      {!cards ? (
        <Loading />
      ) : cards.length === 0 ? (
        <Empty>Nic nie znaleziono. <Link to="/needs">Opisz problem na tablicy potrzeb</Link>.</Empty>
      ) : (
        <>
          <div className="grid">
            {cards.map((c, i) => (
              <InnovationCard key={c.id} card={c} index={i} />
            ))}
          </div>
        </>
      )}
    </>
  )
}
