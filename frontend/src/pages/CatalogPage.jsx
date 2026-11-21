import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, query } from '../api.js'
import InnovationCard from '../components/InnovationCard.jsx'
import { Empty, ErrorBox, Loading, SelectEnum } from '../components/ui.jsx'
import { useApp } from '../context.jsx'

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
      <section className="hero">
        <div>
          <h1>Ukryte innowacje z sektora publicznego w jednym miejscu</h1>
          <p>
            Każdy dobry pomysł ze szkoły, uczelni czy gminy dostaje kartę, przejrzystą ocenę i ścieżkę rozwoju.
            Wdrożenie w kolejnej instytucji to po prostu <strong>fork</strong>.
          </p>
        </div>
        <div className="hero-actions">
          <Link className="btn btn-primary" to="/submit">Zgłoś innowację</Link>
          <Link className="btn" to="/needs">Opisz swoją potrzebę</Link>
        </div>
      </section>

      <div className="search">
        <input
          type="search"
          placeholder="Szukaj problemu lub rozwiązania, np. „kolejki w urzędzie”, „adaptacja pierwszoklasistów”…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Szukaj innowacji"
        />
        <span className="muted small">Wyszukiwanie semantyczne: znajdzie też opisy innymi słowami</span>
      </div>

      <div className="filters">
        <SelectEnum label="Typ" value={filters.type} onChange={set('type')} options={meta.types} />
        <SelectEnum label="Sektor" value={filters.sector} onChange={set('sector')} options={meta.sectors} />
        <SelectEnum label="Etap" value={filters.maturity} onChange={set('maturity')} options={meta.maturities} />
        <SelectEnum label="Model transferu" value={filters.transferModel} onChange={set('transferModel')} options={meta.transferModels} />
        <SelectEnum label="Instytucja" value={filters.institutionType} onChange={set('institutionType')} options={meta.institutionTypes.filter((t) => ['SZKOLA', 'UCZELNIA', 'JST', 'INSTYTUCJA_PUBLICZNA'].includes(t.value))} />
        <SelectEnum label="Status" value={filters.status} onChange={set('status')} options={statuses} />
        <SelectEnum label="Sortuj" value={filters.sort} onChange={(v) => set('sort')(v ?? 'newest')} options={SORTS} placeholder="Domyślnie" />
      </div>

      <ErrorBox>{error}</ErrorBox>
      {!cards ? (
        <Loading />
      ) : cards.length === 0 ? (
        <Empty>Nic nie znaleziono. Może warto opisać ten problem na <Link to="/needs">tablicy potrzeb</Link>?</Empty>
      ) : (
        <>
          <p className="muted small">Znaleziono: {cards.length}</p>
          <div className="grid">
            {cards.map((c) => (
              <InnovationCard key={c.id} card={c} />
            ))}
          </div>
        </>
      )}
    </>
  )
}
