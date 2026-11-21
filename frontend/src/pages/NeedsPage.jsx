import { useState } from 'react'
import { api } from '../api.js'
import MatchRow from '../components/MatchRow.jsx'
import { Badge, Empty, ErrorBox, InstitutionLink, Loading, PersonLink, Section, SelectEnum, Tag } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { formatDate, plural } from '../format.js'
import { useFetch } from '../useFetch.js'

const EMPTY = { title: '', description: '', sector: null, tags: '', wantsReadySolution: true }

/**
 * Tablica potrzeb: instytucja lub firma opisuje problem, a system od razu
 * pokazuje innowacje, które mogą go rozwiązać (z powodami dopasowania).
 */
export default function NeedsPage() {
  const { user, meta, notify } = useApp()
  const { data, setData, loading, error } = useFetch('/needs')
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState(null)
  const [sector, setSector] = useState(null)
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
        ? `Opublikowano potrzebę – pasujące innowacje: ${created.matches.length}.`
        : 'Opublikowano potrzebę. Powiadomimy, gdy pojawi się pasująca innowacja.')
    } catch (e) {
      setFormError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const needle = q.trim().toLowerCase()
  const visible = (data ?? []).filter(({ need }) =>
    (!sector || need.sector?.value === sector)
    && (!needle || `${need.title} ${need.description} ${need.institution?.name}`.toLowerCase().includes(needle)))

  return (
    <>
      <section className="page-head">
        <div>
          <h1>Tablica potrzeb</h1>
          <p className="muted">
            Szkoły, uczelnie, samorządy i firmy opisują problemy, a TrackIdea dopasowuje do nich istniejące innowacje.
            Zamiast wymyślać od nowa – sprawdź, kto już to rozwiązał.
          </p>
        </div>
        {user?.institution && (
          <button className="btn btn-primary" onClick={() => setOpen((o) => !o)}>
            {open ? 'Zamknij formularz' : '+ Opisz potrzebę'}
          </button>
        )}
      </section>

      {!user && <p className="notice small">Wybierz użytkownika demo z instytucji lub firmy, żeby opublikować potrzebę.</p>}

      {open && (
        <Section title="Nowa potrzeba" subtitle={`Publikujesz jako ${user.name} · ${user.institution.name}`}>
          <ErrorBox>{formError}</ErrorBox>
          <div className="form">
            <label className="field wide">
              <span>Jaki macie problem? *</span>
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="Np. Długie kolejki w wydziale komunikacji" />
            </label>
            <label className="field wide">
              <span>Opis – kogo dotyczy, co już próbowaliście, czego szukacie</span>
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
                  <span>Partnera do wspólnego rozwoju (B+R)</span>
                </label>
              </div>
            </div>
          </div>
          <button className="btn btn-primary" disabled={busy || !form.title.trim()} onClick={save}>
            {busy ? 'Szukamy dopasowań…' : 'Opublikuj i znajdź dopasowania'}
          </button>
        </Section>
      )}

      <div className="filters filters-narrow">
        <label className="field">
          <span>Szukaj</span>
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="problem, instytucja…" />
        </label>
        <SelectEnum label="Sektor" value={sector} onChange={setSector} options={meta.sectors} />
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
                    {need.wantsReadySolution ? 'Szuka gotowego rozwiązania' : 'Szuka partnera do rozwoju'}
                  </Badge>
                  {need.institution && !need.institution.publicSector && <Badge tone="warn">{need.institution.type.label}</Badge>}
                </div>
                <h3>{need.title}</h3>
                <p className="muted small">
                  <InstitutionLink institution={need.institution} /> · <PersonLink person={need.author} /> · {formatDate(need.createdAt)}
                </p>
                {need.description && <p className="small">{need.description}</p>}
                <div className="tags">{[...(need.tags ?? [])].map((t) => <Tag key={t}>{t}</Tag>)}</div>
              </div>
              <div className="need-matches">
                <h4>{matches.length} {plural(matches.length, 'pasująca innowacja', 'pasujące innowacje', 'pasujących innowacji')}</h4>
                {matches.length === 0
                  ? <p className="muted small">Na razie brak – to może być pomysł na nową innowację.</p>
                  : matches.slice(0, 3).map((m) => <MatchRow key={m.id} m={m} />)}
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  )
}
