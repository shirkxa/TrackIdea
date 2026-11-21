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
  { value: 'newest', label: 'Najnowsze / trafność' },
  { value: 'score', label: 'Najlepiej ocenione' },
  { value: 'popular', label: 'Najpopularniejsze' },
  { value: 'forks', label: 'Najczęściej forkowane' },
]

export default function CatalogPage() {
  const { meta } = useApp()
  const [filters, setFilters] = useState({ sort: 'newest' })
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

      <div className="filters">
        <SelectEnum compact label="Typ" value={filters.type} onChange={set('type')} options={meta.types} />
        <SelectEnum compact label="Sektor" value={filters.sector} onChange={set('sector')} options={meta.sectors} />
        <SelectEnum compact label="Etap" value={filters.maturity} onChange={set('maturity')} options={meta.maturities} />
        <SelectEnum compact label="Transfer" value={filters.transferModel} onChange={set('transferModel')} options={meta.transferModels} />
        <SelectEnum compact label="Instytucja" value={filters.institutionType} onChange={set('institutionType')} options={meta.institutionTypes.filter((t) => ['SZKOLA', 'UCZELNIA', 'JST', 'INSTYTUCJA_PUBLICZNA'].includes(t.value))} />
        <SelectEnum compact label="Status" value={filters.status} onChange={set('status')} options={statuses} />
        <SelectEnum compact label="Sortuj: najnowsze" value={filters.sort === 'newest' ? null : filters.sort} onChange={(v) => set('sort')(v ?? 'newest')} options={SORTS.slice(1)} />
      </div>

      <ErrorBox>{error}</ErrorBox>
      {!cards ? (
        <Loading />
      ) : cards.length === 0 ? (
        <Empty>Nic nie znaleziono. <Link to="/needs">Opisz problem na tablicy potrzeb</Link>.</Empty>
      ) : (
        <>
          <p className="muted small results-count">{cards.length} wyników</p>
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
