import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../api.js'
import Icon from '../components/Icon.jsx'
import InnovationCard from '../components/InnovationCard.jsx'
import { Empty, ErrorBox, Loading, SelectEnum } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { useFetch } from '../useFetch.js'

const TILES = [
  { to: '/pci', icon: 'radar', label: 'Radar' },
  { to: '/needs', icon: 'needs', label: 'Szukam rozwiązania' },
  { to: '/pci', icon: 'chart', label: 'Mierniki' },
]

const SORTS = [
  { value: 'newest', label: 'Najnowsze' },
  { value: 'score', label: 'Najlepiej ocenione' },
  { value: 'updated', label: 'Ostatnio aktualizowane' },
  { value: 'trl', label: 'Najwyższy TRL' },
]

const PUBLIC_INSTITUTIONS = ['SZKOLA', 'UCZELNIA', 'JST', 'INSTYTUCJA_PUBLICZNA']

export default function CatalogPage() {
  const { meta } = useApp()
  const [filters, setFilters] = useState({ sort: 'newest' })
  const [panelOpen, setPanelOpen] = useState(false)
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [inst, setInst] = useState('')
  const [debouncedInst, setDebouncedInst] = useState('')
  const institutions = useFetch('/institutions')
  const [cards, setCards] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 300)
    return () => clearTimeout(t)
  }, [q])

  useEffect(() => {
    const t = setTimeout(() => setDebouncedInst(inst.trim()), 300)
    return () => clearTimeout(t)
  }, [inst])

  const params = { ...filters, q: debouncedQ, institution: debouncedInst }

  useEffect(() => {
    api
      .get(`/innovations${query({ ...filters, q: debouncedQ, institution: debouncedInst })}`)
      .then((d) => {
        setCards(d)
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [filters, debouncedQ, debouncedInst])

  const set = (key) => (value) => setFilters((f) => ({ ...f, [key]: value }))
  const statuses = meta.statuses.filter((s) => s.step > 0).map((s) => ({ value: s.value, label: s.label }))

  // Filtry w panelu „Filtry” – pokazywane potem jako usuwalne etykiety.
  const panelFilters = [
    { key: 'type', label: 'Typ innowacji', options: meta.types },
    { key: 'sector', label: 'Sektor', options: meta.sectors },
    { key: 'maturity', label: 'Etap', options: meta.maturities },
    { key: 'minTrl', label: 'TRL', options: [3, 5, 7, 9].map((n) => ({ value: String(n), label: n === 9 ? 'TRL 9' : `TRL ${n}+` })) },
    { key: 'transferModel', label: 'Model transferu', options: meta.transferModels },
    { key: 'institutionType', label: 'Instytucja', options: meta.institutionTypes.filter((t) => PUBLIC_INSTITUTIONS.includes(t.value)) },
    { key: 'status', label: 'Status', options: statuses },
  ]
  const active = panelFilters
    .filter((f) => filters[f.key])
    .map((f) => ({ ...f, valueLabel: f.options.find((o) => o.value === filters[f.key])?.label }))
  const clearAll = () => {
    setFilters((f) => ({ sort: f.sort, area: f.area }))
    setInst('')
  }
  const sortLabel = debouncedQ && filters.sort === 'newest' ? 'Najtrafniejsze' : null

  return (
    <>
      <h1 className="page-title">Katalog innowacji</h1>
      <details className="definition">
        <summary>Co uznajemy za innowację?</summary>
        <p>
          Za Podręcznikiem Oslo (OECD/Eurostat, 2018): <b>nowy lub ulepszony produkt albo proces</b>, który znacząco
          różni się od dotychczasowych i został już udostępniony użytkownikom lub wdrożony. Radar szuka rozwiązań,
          które już istnieją – w trzech obszarach:
        </p>
        <ul>
          {meta.areas.map((a) => <li key={a.value}><b>{a.label}</b> – {a.description}</li>)}
        </ul>
      </details>

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
        <label className="search search-inst">
          <Icon name="building" size={20} />
          <input
            type="search"
            list="institution-names"
            placeholder="Instytucja lub firma"
            value={inst}
            onChange={(e) => setInst(e.target.value)}
            aria-label="Szukaj po nazwie instytucji lub firmy"
          />
          <datalist id="institution-names">
            {institutions.data?.map((i) => <option key={i.id} value={i.name} />)}
          </datalist>
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
        <div className="type-tabs" role="tablist" aria-label="Obszar innowacji">
          <button type="button" role="tab" aria-selected={!filters.area} className={!filters.area ? 'on' : ''}
            onClick={() => set('area')(null)}>
            Wszystkie
          </button>
          {meta.areas.map((t) => (
            <button key={t.value} type="button" role="tab" aria-selected={filters.area === t.value}
              className={filters.area === t.value ? 'on' : ''} onClick={() => set('area')(t.value)}
              title={t.description}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="results-bar">
        <span className="muted small results-count">{cards ? `${cards.length} wyników` : ''}</span>
        <a className="link-button export-link" href={`${import.meta.env.VITE_API_URL ?? ''}/api/innovations/export${query(params)}`}
          download title="Pobierz wyniki jako CSV (Excel)">
          <Icon name="download" size={16} /> CSV
        </a>
        <label className="sort">
          <span className="muted small">Sortuj:</span>
          <select value={filters.sort} onChange={(e) => set('sort')(e.target.value)} aria-label="Sortowanie">
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>{o.value === 'newest' && sortLabel ? sortLabel : o.label}</option>
            ))}
          </select>
        </label>
      </div>

      {(active.length > 0 || debouncedInst) && (
        <div className="active-filters">
          {debouncedInst && (
            <button type="button" className="chip chip-on" onClick={() => setInst('')} title="Usuń filtr">
              Instytucja: {debouncedInst} ✕
            </button>
          )}
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
        <Empty>Nic nie znaleziono. <Link to="/needs">Opisz, czego szukasz</Link>.</Empty>
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
