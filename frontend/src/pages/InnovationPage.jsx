import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { formatDate } from '../format.js'
import {
  Badge, Empty, ErrorBox, InstitutionLink, Loading, Pipeline, ScoreRing, Section, StatusBadge, Tag,
} from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { useFetch } from '../useFetch.js'
import MatchRow from '../components/MatchRow.jsx'
import InstitutionLogo from '../components/InstitutionLogo.jsx'
import { targetLink } from '../links.js'

export default function InnovationPage() {
  const { id } = useParams()
  const { user, meta, notify } = useApp()
  const { data, setData, loading, error } = useFetch(`/innovations/${id}`, [user?.id])
  const [version, setVersion] = useState(0)
  const [busy, setBusy] = useState(false)

  if (loading && !data) return <Loading />
  if (error) return <ErrorBox>{error}</ErrorBox>
  if (!data) return null

  const { card } = data

  /** Akcja analityka na rekordzie; odświeża widok (status, ocena, zastosowania). */
  async function act(path, body, okMessage) {
    setBusy(true)
    try {
      const res = await api.post(`/innovations/${card.id}${path}`, body)
      if (res?.card) setData(res)
      setVersion((v) => v + 1)
      if (okMessage) notify(okMessage)
      return res
    } catch (e) {
      notify(e.message, 'error')
      return null
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="innovation-page">
      <header className="innovation-head card">
        <InstitutionLogo institution={card.institution} sector={card.sector} size={88} />
        <div className="head-main">
          <div className="card-badges">
            <StatusBadge status={card.status} />
            {card.type && <Badge tone="muted">{card.type.label}</Badge>}
            {card.sector && <Badge tone="muted">{card.sector.label}</Badge>}
            {card.sourceName && <Badge tone="radar">Źródło: {card.sourceName}</Badge>}
          </div>
          <h1>{card.title}</h1>
          <p className="lead">{card.summary}</p>
          <p className="muted small">
            <InstitutionLink institution={card.institution} />
            {card.authors && <> · Autorzy: {card.authors}</>}
            {' · '}w Radarze od {formatDate(card.createdAt)}
          </p>
          {data.links?.length > 0 && (
            <a className="btn btn-small source-link" href={[...data.links][0]} target="_blank" rel="noreferrer">
              Zobacz w źródle ({card.sourceName}) ↗
            </a>
          )}
        </div>
        <div className="head-side">
          <ScoreRing score={card.score} size={84} estimated={card.scoreEstimated} />
        </div>
      </header>

      <div className="card pipeline-card">
        <Pipeline statuses={meta.statuses} current={card.status} />
      </div>

      <ActionPanel data={data} busy={busy} act={act} />

      <div className="two-col">
        <div className="col-main">
          <CardSection data={data} />
          <AssessmentSection data={data} busy={busy} act={act} />
          <PathsSection data={data} busy={busy} act={act} version={version} />
          <TimelineSection events={data.events} />
        </div>
        <aside className="col-side">
          <MatchesSection id={card.id} version={version} />
          <SimilarSection id={card.id} />
        </aside>
      </div>
    </div>
  )
}

/** Akcje analityka PCI: popraw kartę, przygotuj ocenę, odrzuć rekord. */
function ActionPanel({ data, busy, act }) {
  const { card, viewer, completeness } = data
  const [rejectOpen, setRejectOpen] = useState(false)
  const [reason, setReason] = useState('')
  const actions = []

  if (viewer.canEdit) {
    actions.push(<Link key="edit" className="btn" to={`/innovations/${card.id}/edit`}>Popraw kartę</Link>)
  }
  if (viewer.canPropose) {
    actions.push(
      <button key="propose" className="btn btn-primary" disabled={busy}
        onClick={() => act('/propose-assessment', {}, 'Ocena gotowa do weryfikacji.')}>
        {card.status.value === 'OCENIONA' ? 'Oceń ponownie' : 'Zweryfikuj ocenę'}
      </button>,
    )
  }
  if (viewer.canReject) {
    actions.push(
      <button key="reject" className="btn" onClick={() => setRejectOpen((o) => !o)}>Odrzuć rekord</button>,
    )
  }
  if (viewer.canRestore) {
    actions.push(
      <button key="restore" className="btn btn-primary" disabled={busy}
        onClick={() => act('/restore', {}, 'Rekord wrócił do katalogu.')}>
        Przywróć do katalogu
      </button>,
    )
  }

  const hints = []
  if (card.status.value === 'WYKRYTA') hints.push('Rekord z Radaru – czeka na weryfikację analityka PCI.')
  if (card.status.value === 'W_OCENIE') hints.push(viewer.canApprove ? 'Sprawdź i zatwierdź ocenę poniżej.' : 'Ocena czeka na analityka PCI.')
  if (card.status.step === 0) {
    hints.push(card.autoRejected
      ? 'Odrzucona automatycznie: według analizy AI to nie jest innowacja (powód na osi czasu). Decyzję może zmienić analityk PCI.'
      : card.status.description)
  }

  if (actions.length === 0 && hints.length === 0 && completeness.percent === 100) return null

  return (
    <div className="card action-panel">
      {completeness.percent < 100 && (
        <div className="completeness">
          <div className="progress"><div style={{ width: `${completeness.percent}%` }} /></div>
          <span className="small">Karta wypełniona w {completeness.percent}%. Brakuje: {completeness.missing.join(', ')}.</span>
        </div>
      )}
      {hints.map((h) => <p key={h} className="small muted">{h}</p>)}
      {actions.length > 0 && <div className="actions">{actions}</div>}
      {rejectOpen && (
        <div className="contact-form">
          <textarea placeholder="Powód, np. nie wdrożone ani nie udostępnione użytkownikom (Oslo Manual 2018)"
            value={reason} onChange={(e) => setReason(e.target.value)} />
          <button className="btn btn-primary" disabled={busy}
            onClick={async () => {
              const ok = await act('/reject', { reason }, 'Rekord odrzucony – nie będzie widoczny w katalogu.')
              if (ok) setRejectOpen(false)
            }}>
            Odrzuć
          </button>
        </div>
      )}
    </div>
  )
}

function CardSection({ data }) {
  const { card } = data
  const est = new Set(card.estimated ?? [])
  const engine = data.analysisEngine === 'CLAUDE' ? 'model AI' : 'reguły offline'

  const rows = [
    ['Problem', data.problem],
    ['Rozwiązanie', data.solution],
    ['Wymagania wdrożenia', data.requirements],
    ['Efekty', data.results],
  ]
  return (
    <Section title="Opis">
      {est.has('content') && (
        <p className="muted small legend">
          <Badge tone="estimate">szacunek</Badge> Opis przygotował automat ({engine}) na podstawie tekstu ze źródła –
          do sprawdzenia przez analityka PCI. Oryginał jest pod linkiem do źródła.
        </p>
      )}
      <dl className="readme">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value || <span className="muted">— brak w źródle</span>}</dd>
          </div>
        ))}
      </dl>
      <h3 className="sub-title">Klasyfikacja</h3>
      <div className="facts">
        <Fact label="Obszar" estimated={est.has('area')}>{card.area?.label}</Fact>
        <Fact label="Typ innowacji" estimated={est.has('type')}>{card.type?.label}</Fact>
        <Fact label="Sektor" estimated={est.has('sector')}>{card.sector?.label}</Fact>
        <Fact label="Etap wdrożenia" estimated={est.has('maturity')}>
          {card.maturity?.label ?? <span className="muted">nieustalony – źródło go nie podaje</span>}
        </Fact>
        <Fact label="Gotowość (TRL)" estimated={est.has('trl')}>
          {card.trl && <>TRL {card.trl.level} <span className="muted small">· {card.trl.label}</span></>}
        </Fact>
        <Fact label="Model transferu">{card.transferModel?.label}</Fact>
        <Fact label="Ochrona IP">{data.ipStatus}</Fact>
      </div>
      <p className="muted small legend">
        <Badge tone="estimate">szacunek</Badge> ustalone automatycznie ({engine}) – do potwierdzenia przez analityka PCI;
        pozostałe dane podało źródło lub potwierdził analityk.
      </p>

      <h3 className="sub-title">Fakty ze źródła</h3>
      <ul className="source-facts">
        {card.sourceName && <li>Źródło: <b>{card.sourceName}</b>{data.sourceRef && <> · {data.sourceRef}</>}</li>}
        {card.institution && <li>Instytucja: <b>{card.institution.name}</b></li>}
        {card.authors && <li>Autorzy: {card.authors}</li>}
        {data.sourceYear && !data.sourceFacts?.some((f) => f.startsWith('Rok')) && <li>Rok: {data.sourceYear}</li>}
        {data.sourceFacts?.filter((f) => !f.startsWith('Polska')).map((f) => <li key={f}>{f}</li>)}
      </ul>
      {card.tags?.length > 0 && (
        <div className="tags">{[...card.tags].map((t) => <Tag key={t}>{t}</Tag>)}</div>
      )}
      {data.links?.length > 0 && (
        <ul className="links">
          {[...data.links].map((l) => (
            <li key={l}><a href={l} target="_blank" rel="noreferrer">{l}</a></li>
          ))}
        </ul>
      )}
    </Section>
  )
}

function Fact({ label, estimated, children }) {
  if (!children) return null
  return (
    <div className="fact">
      <span className="muted small">
        {label} {estimated && <Badge tone="estimate" title="Ustalone automatycznie – do potwierdzenia">szacunek</Badge>}
      </span>
      <strong>{children}</strong>
    </div>
  )
}

/** Karta oceny z jawnymi wagami. Analityk może poprawić każde kryterium przed zatwierdzeniem. */
function AssessmentSection({ data, busy, act }) {
  const { assessment, viewer } = data
  const [edits, setEdits] = useState({})

  if (!assessment) {
    return (
      <Section title="Ocena potencjału">
        <Empty>Jeszcze nieoceniona.</Empty>
      </Section>
    )
  }

  const editing = viewer.canApprove
  // kryterium, które nie ma zastosowania (TRL poza technologiami), nie wchodzi do oceny – wagi pozostałych zachowują proporcje
  const weights = assessment.scores.reduce((sum, s) => sum + s.criterion.weight, 0)
  const hasTrl = assessment.scores.some((s) => s.criterion.value === 'TRL')
  const total = editing
    ? assessment.scores.reduce((sum, s) => {
        const v = edits[s.criterion.value]?.score ?? s.proposedScore
        return sum + (s.criterion.weight / weights) * ((v - 1) / 4) * 100
      }, 0)
    : assessment.approved ? assessment.total : assessment.proposedTotal

  return (
    <Section
      title="Ocena potencjału"
      subtitle={assessment.approved
        ? `Zatwierdzona przez ${assessment.approvedBy?.name ?? 'analityka PCI'} · ${formatDate(assessment.approvedAt)}`
        : data.card.status.value === 'W_OCENIE'
          ? 'Propozycja systemu – analityk PCI sprawdza i zatwierdza'
          : 'Ocena automatyczna (szacunek) – jeszcze niezweryfikowana przez analityka PCI'}
      actions={<ScoreRing score={total} size={64} estimated={!assessment.approved} />}
    >
      <table className="score-table">
        <thead>
          <tr>
            <th>Kryterium</th>
            <th className="num">Waga</th>
            <th>Ocena</th>
            <th>Uzasadnienie</th>
          </tr>
        </thead>
        <tbody>
          {assessment.scores.map((s) => {
            const value = editing ? edits[s.criterion.value]?.score ?? s.proposedScore : s.finalScore ?? s.proposedScore
            const changed = s.finalScore != null && s.finalScore !== s.proposedScore
            return (
              <tr key={s.criterion.value}>
                <td>
                  <strong>{s.criterion.label}</strong>
                  <div className="muted small">{s.criterion.question}</div>
                </td>
                <td className="num">{s.criterion.weight}%</td>
                <td className="score-cell">
                  {editing ? (
                    <select value={value} onChange={(e) => setEdits((x) => ({
                      ...x, [s.criterion.value]: { ...x[s.criterion.value], score: Number(e.target.value) },
                    }))}>
                      {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
                    </select>
                  ) : (
                    <span className="score-value">{value}/5</span>
                  )}
                  <div className="dots">
                    {[1, 2, 3, 4, 5].map((n) => <i key={n} className={n <= value ? 'on' : ''} />)}
                  </div>
                  {changed && <div className="small muted">propozycja: {s.proposedScore}</div>}
                </td>
                <td className="small">
                  {editing ? (
                    <textarea rows={2} defaultValue={s.proposedJustification}
                      onChange={(e) => setEdits((x) => ({
                        ...x, [s.criterion.value]: { ...x[s.criterion.value], justification: e.target.value },
                      }))} />
                  ) : (
                    s.finalJustification ?? s.proposedJustification
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="muted small">
        Wynik = Σ waga × (ocena − 1) / 4 × 100, liczony z wag użytych kryteriów.
        {!hasTrl && ' Gotowość technologiczna (TRL) nie dotyczy tej innowacji, więc nie wchodzi do oceny.'}
        {!assessment.approved && ' Automat nie przyznaje najwyższej oceny za innowacyjność – może to zrobić analityk.'}
      </p>
      {editing && (
        <button className="btn btn-primary" disabled={busy}
          onClick={() => act('/assessment/approve', {
            scores: assessment.scores.map((s) => ({
              criterion: s.criterion.value,
              score: edits[s.criterion.value]?.score ?? s.proposedScore,
              justification: edits[s.criterion.value]?.justification ?? null,
            })),
          }, 'Ocena zatwierdzona.')}>
          Zatwierdź ocenę
        </button>
      )}
    </Section>
  )
}

/** Gdzie innowacja może znaleźć zastosowanie – rekomendacje z jawnych reguł. */
function PathsSection({ data, busy, act, version }) {
  const { card, viewer } = data
  const [recs, setRecs] = useState(null)
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    api.get(`/innovations/${card.id}/recommendations`).then(setRecs).catch(() => setRecs([]))
  }, [card.id, version])

  const chosen = selected ?? new Set(data.chosenPaths.map((p) => p.value))
  const choosing = viewer.canChoosePaths

  return (
    <Section title="Zastosowania">
      {data.chosenPaths.length > 0 && (
        <p>Wskazane przez PCI: {data.chosenPaths.map((p) => <Badge key={p.value} tone="accent">{p.label}</Badge>)}</p>
      )}
      {!recs ? <Loading /> : recs.map((r) => (
        <article key={r.path} className={`path ${r.chosen ? 'path-chosen' : ''}`}>
          <header>
            {choosing && (
              <input type="checkbox" checked={chosen.has(r.path)} aria-label={`Wybierz: ${r.label}`}
                onChange={(e) => {
                  const next = new Set(chosen)
                  if (e.target.checked) next.add(r.path)
                  else next.delete(r.path)
                  setSelected(next)
                }} />
            )}
            <h3>{r.label}</h3>
            <Badge tone="info">dopasowanie {r.fit}%</Badge>
          </header>
          <p className="small"><span className="muted">Reguła:</span> {r.rule}</p>
          <p className="small"><span className="muted">Dlaczego:</span> {r.reasons.join(' · ')}</p>
          <ol className="steps small">{r.nextSteps.map((s) => <li key={s}>{s}</li>)}</ol>
          {r.targets.length > 0 && (
            <div className="targets">
              {r.targets.map((t) => (
                <Link key={`${t.kind}-${t.id}`} className="target" to={targetLink(t)}>
                  {t.title} <span className="muted">· {t.score}%</span>
                </Link>
              ))}
            </div>
          )}
        </article>
      ))}
      {choosing && (
        <button className="btn btn-primary" disabled={busy || chosen.size === 0}
          onClick={() => act('/paths', { paths: [...chosen] }, 'Zapisano zastosowania.')}>
          Zapisz wybrane zastosowania
        </button>
      )}
    </Section>
  )
}

function MatchesSection({ id, version }) {
  const { data } = useFetch(`/innovations/${id}/matches`, [version])
  return (
    <Section title="Pasuje do zapytań">
      {!data ? <Loading /> : data.needs.length === 0
        ? <p className="muted small">Brak pasujących zapytań.</p>
        : data.needs.map((m) => <MatchRow key={`n${m.id}`} m={m} />)}
    </Section>
  )
}

function SimilarSection({ id }) {
  const { data } = useFetch(`/innovations/${id}/similar`)
  return (
    <Section title="Podobne">
      {!data ? <Loading /> : (
        <>
          {data.innovations.length === 0 && data.priorArt.length === 0 && <p className="muted small">Nie znaleziono podobnych.</p>}
          {data.innovations.map((s) => (
            <div key={`i${s.id}`} className="similar">
              <Link to={`/innovations/${s.id}`}>{s.title}</Link> <Badge tone={s.similarityPercent >= 75 ? 'warn' : 'muted'}>{s.similarityPercent}%</Badge>
              <div className="muted small">{s.subtitle}</div>
            </div>
          ))}
          {data.priorArt.map((s) => (
            <div key={`p${s.id}`} className="similar">
              <Badge tone="radar">{s.kind === 'PATENT' ? 'Patent' : 'Publikacja'}</Badge> {s.title} <Badge tone="muted">{s.similarityPercent}%</Badge>
              <div className="muted small">{s.subtitle}</div>
            </div>
          ))}
        </>
      )}
    </Section>
  )
}

function TimelineSection({ events }) {
  return (
    <Section title="Oś czasu">
      <ol className="timeline">
        {[...events].reverse().map((e) => (
          <li key={e.id}>
            <span className="when">{formatDate(e.createdAt)}</span>
            <div>
              <div>{e.message}</div>
              <div className="muted small">
                {e.type.label}
                {e.actor && <> · {e.actor.name}</>}
              </div>
            </div>
          </li>
        ))}
      </ol>
    </Section>
  )
}
