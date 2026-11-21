import { useState } from 'react'
import { api } from '../api.js'
import MatchRow from '../components/MatchRow.jsx'
import { Badge, Empty, ErrorBox, InstitutionLink, Loading, Section, SelectEnum, Tag } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { formatDate, plural } from '../format.js'
import { useFetch } from '../useFetch.js'

const EMPTY = { title: '', description: '', sector: null, tags: '', wantsReadySolution: true }

/** Skąd pochodzi potrzeba: zgłoszona w formularzu albo pobrana przez Radar z ogłoszeń o zamówieniach. */
const ORIGINS = [
  { value: 'FORM', label: 'Zgłoszone w TrackIdea' },
  { value: 'SOURCE', label: 'Zamówienia publiczne (BZP)' },
]

/** Opisy z ogłoszeń bywają długie – na karcie skrót, całość po kliknięciu. */
function Description({ text }) {
  const [full, setFull] = useState(false)
  if (!text) return null
  const long = text.length > 420
  return (
    <p className="small">
      {long && !full ? `${text.slice(0, 400).trimEnd()}… ` : `${text} `}
      {long && (
        <button type="button" className="link-button" onClick={() => setFull((f) => !f)}>
          {full ? 'zwiń' : 'pokaż całość'}
        </button>
      )}
    </p>
  )
}

/**
 * Tablica potrzeb: instytucja lub firma opisuje problem, a system od razu
 * pokazuje innowacje, które mogą go rozwiązać (z powodami dopasowania).
 * Obok zgłoszeń z formularza są tu potrzeby z otwartych źródeł (ogłoszenia BZP) – z linkiem i terminem.
 */
export default function NeedsPage() {
  const { user, meta, notify } = useApp()
  const { data, setData, loading, error } = useFetch('/needs')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState(null)
  const [sector, setSector] = useState(null)
  const [origin, setOrigin] = useState(null)
  const [q, setQ] = useState('')

  async function save() {
    setBusy(true)
    setFormError(null)
    try {
      const created = await api.post('/needs', {
        ...form,
        tags: form.tags.split(',').map((t) => t.trim()).filter(Boolean),
      })
      setData((list) => [created, ...(list ?? [])])
      setForm(EMPTY)
      setOpen(false)
      notify(created.matches.length > 0
        ? `Znalezione rozwiązania: ${created.matches.length}.`
        : 'Na razie brak pasujących innowacji – Radar sprawdzi nowe rekordy.')
    } catch (e) {
      setFormError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const needle = q.trim().toLowerCase()
  const visible = (data ?? []).filter(({ need }) =>
    (!sector || need.sector?.value === sector)
    && (!origin || (origin === 'SOURCE') === Boolean(need.source))
    && (!needle || `${need.title} ${need.description} ${need.institution?.name}`.toLowerCase().includes(needle)))

  return (
    <>
      <section className="page-head">
        <div>
          <h1 className="page-title">Szukam rozwiązania</h1>
        </div>
        {user && (
          <button className="btn btn-primary" onClick={() => setOpen((o) => !o)}>
            {open ? 'Zamknij formularz' : '+ Nowe zapytanie'}
          </button>
        )}
      </section>


      {open && (
        <Section title="Czego szukasz?">
          <ErrorBox>{formError}</ErrorBox>
          <div className="form">
            <label className="field wide">
              <span>Problem do rozwiązania *</span>
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="Np. Długie kolejki w wydziale komunikacji" />
            </label>
            <label className="field wide">
              <span>Opis</span>
              <textarea rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </label>
            <SelectEnum label="Sektor" value={form.sector} onChange={(v) => setForm({ ...form, sector: v })}
              options={meta.sectors} placeholder="Wybierz…" />
            <label className="field">
              <span>Tagi (po przecinku)</span>
              <input value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} placeholder="kolejki, urząd" />
            </label>
            <div className="field wide">
              <span>Czego szukacie?</span>
              <div className="radio-row">
                <label className="checkbox">
                  <input type="radio" checked={form.wantsReadySolution} onChange={() => setForm({ ...form, wantsReadySolution: true })} />
                  <span>Gotowego rozwiązania do wdrożenia</span>
                </label>
                <label className="checkbox">
                  <input type="radio" checked={!form.wantsReadySolution} onChange={() => setForm({ ...form, wantsReadySolution: false })} />
                  <span>Rozwiązania we wczesnej fazie (do rozwoju)</span>
                </label>
              </div>
            </div>
          </div>
          <button className="btn btn-primary" disabled={busy || !form.title.trim()} onClick={save}>
            {busy ? 'Szukamy dopasowań…' : 'Znajdź rozwiązania'}
          </button>
        </Section>
      )}

      <div className="filters filters-narrow">
        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Szukaj problemu lub instytucji" aria-label="Szukaj" />
        <SelectEnum compact label="Sektor" value={sector} onChange={setSector} options={meta.sectors} />
        <SelectEnum compact label="Pochodzenie" value={origin} onChange={setOrigin} options={ORIGINS} />
      </div>

      <ErrorBox>{error}</ErrorBox>
      {loading && !data ? (
        <Loading />
      ) : visible.length === 0 ? (
        <Empty>Brak potrzeb dla wybranych filtrów.</Empty>
      ) : (
        <div className="needs">
          {visible.map(({ need, matches }) => (
            <article key={need.id} className="card need">
              <div className="need-body">
                <div className="card-badges">
                  {need.sector && <Badge tone="muted">{need.sector.label}</Badge>}
                  <Badge tone={need.wantsReadySolution ? 'info' : 'radar'}>
                    {need.wantsReadySolution ? 'Gotowe rozwiązanie' : 'Wczesna faza'}
                  </Badge>
                  {need.institution && !need.institution.publicSector && <Badge tone="warn">{need.institution.type.label}</Badge>}
                  {need.source && <Badge tone="radar" title="Pobrane automatycznie z otwartego źródła">{need.source}</Badge>}
                </div>
                <h3>{need.title}</h3>
                <p className="muted small">
                  <InstitutionLink institution={need.institution} /> · {formatDate(need.createdAt)}
                  {need.deadline && <> · oferty do {formatDate(need.deadline)}</>}
                </p>
                <Description text={need.description} />
                <div className="tags">{[...(need.tags ?? [])].map((t) => <Tag key={t}>{t}</Tag>)}</div>
                {need.sourceUrl && (
                  <p className="small">
                    <a href={need.sourceUrl} target="_blank" rel="noreferrer">Ogłoszenie {need.sourceRef} ↗</a>
                  </p>
                )}
              </div>
              <div className="need-matches">
                <h4>{matches.length} {plural(matches.length, 'pasująca innowacja', 'pasujące innowacje', 'pasujących innowacji')}</h4>
                {matches.length === 0
                  ? <p className="muted small">Brak.</p>
                  : matches.slice(0, 3).map((m) => <MatchRow key={m.id} m={m} />)}
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  )
}
