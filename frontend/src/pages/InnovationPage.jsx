import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { formatDate } from '../format.js'
import {
  Badge, Empty, ErrorBox, InstitutionLink, Loading, PersonLink, Pipeline, ScoreRing, Section, StatusBadge, Tag,
} from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { useFetch } from '../useFetch.js'

export default function InnovationPage() {
  const { id } = useParams()
  const { user, meta, notify } = useApp()
  const navigate = useNavigate()
  const { data, setData, loading, error } = useFetch(`/innovations/${id}`, [user?.id])
  const [version, setVersion] = useState(0)
  const [busy, setBusy] = useState(false)

  if (loading && !data) return <Loading />
  if (error) return <ErrorBox>{error}</ErrorBox>
  if (!data) return null

  const { card, viewer } = data

  /** Wywołuje akcję na innowacji i odświeża widok (status, ocena, rekomendacje). */
  async function act(path, body, okMessage) {
    setBusy(true)
    try {
      const res = await api.post(`/innovations/${card.id}${path}`, body)
      if (res?.card) {
        if (res.card.id !== card.id) {
          navigate(`/innovations/${res.card.id}`)
        } else {
          setData(res)
        }
      }
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

  async function toggle(kind) {
    try {
      const res = await api.post(`/innovations/${card.id}/${kind}`)
      setData((d) => ({
        ...d,
        card: {
          ...d.card,
          votes: kind === 'vote' ? d.card.votes + (res.voted ? 1 : -1) : d.card.votes,
          followers: kind === 'follow' ? d.card.followers + (res.following ? 1 : -1) : d.card.followers,
        },
        viewer: { ...d.viewer, voted: res.voted ?? d.viewer.voted, following: res.following ?? d.viewer.following },
      }))
    } catch (e) {
      notify(e.message, 'error')
    }
  }

  return (
    <div className="innovation-page">
      <header className="innovation-head card">
        <div className="head-main">
          <div className="card-badges">
            <StatusBadge status={card.status} />
            {card.type && <Badge tone="muted">{card.type.label}</Badge>}
            {card.sector && <Badge tone="muted">{card.sector.label}</Badge>}
            {card.source?.value === 'RADAR' && <Badge tone="radar">Wykryta przez Radar · {card.sourceName}</Badge>}
            {card.source?.value === 'ZWIADOWCA' && <Badge tone="warn">Zgłoszona przez zwiadowcę</Badge>}
          </div>
          <h1>{card.title}</h1>
          <p className="lead">{card.summary}</p>
          <p className="muted small">
            <InstitutionLink institution={card.institution} />
            {card.authors.length > 0 && (
              <>
                {' · '}Autorzy:{' '}
                {card.authors.map((a, i) => (
                  <span key={a.id}>
                    {i > 0 && ', '}
                    <PersonLink person={a} />
                  </span>
                ))}
              </>
            )}
            {card.authors.length === 0 && data.sourceAuthors && <> · Autorzy w źródle: {data.sourceAuthors}</>}
            {' · '}dodano {formatDate(card.createdAt)}
          </p>
          {card.forkedFrom && (
            <p className="notice">
              To wdrożenie (fork) innowacji <Link to={`/innovations/${card.forkedFrom.id}`}>{card.forkedFrom.name}</Link>.
            </p>
          )}
        </div>
        <div className="head-side">
          <ScoreRing score={card.score} size={84} />
          <div className="head-buttons">
            <button className={`btn btn-small ${viewer.voted ? 'btn-on' : ''}`} disabled={!user} onClick={() => toggle('vote')}
              title="Głos jak w Banku Pomysłów: przydałoby się u nas">
              ▲ Przydałoby się u nas · {card.votes}
            </button>
            <button className={`btn btn-small ${viewer.following ? 'btn-on' : ''}`} disabled={!user} onClick={() => toggle('follow')}>
              ☆ {viewer.following ? 'Obserwujesz' : 'Obserwuj'} · {card.followers}
            </button>
          </div>
        </div>
      </header>

      <div className="card pipeline-card">
        <Pipeline statuses={meta.statuses} current={card.status} />
        <p className="muted small center">{card.status.description}</p>
      </div>

      <ActionPanel data={data} user={user} busy={busy} act={act} />

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
          <ForksSection forks={data.forks} deployments={card.deployments} />
        </aside>
      </div>
    </div>
  )
}

/** Akcje zależne od roli i statusu – „co dalej” z tą innowacją. */
function ActionPanel({ data, user, busy, act }) {
  const { card, viewer, completeness } = data
  const [contactOpen, setContactOpen] = useState(false)
  const [message, setMessage] = useState('')
  const actions = []

  if (viewer.canConfirm) {
    actions.push(
      <button key="confirm" className="btn btn-primary" disabled={busy}
        onClick={() => act('/confirm', {}, 'Potwierdzono – innowacja jest publiczna.')}>
        {card.authors.some((a) => a.id === user?.id) || user?.role.value !== 'TWORCA'
          ? 'Potwierdź zgłoszenie'
          : 'To mój pomysł – potwierdzam autorstwo'}
      </button>,
    )
  }
  if (viewer.canEdit) {
    actions.push(<Link key="edit" className="btn" to={`/innovations/${card.id}/edit`}>Edytuj kartę</Link>)
  }
  if (viewer.canRequestAssessment) {
    actions.push(
      <button key="assess" className="btn btn-primary" disabled={busy}
        onClick={() => act('/request-assessment', {}, 'Wysłano do oceny – propozycja czeka na eksperta PCI.')}>
        {card.status.value === 'UZUPELNIONA' ? 'Poproś o ocenę' : 'Poproś o ponowną ocenę'}
      </button>,
    )
  }
  if (viewer.canFork) {
    actions.push(
      <button key="fork" className="btn btn-good" disabled={busy}
        onClick={() => act('/fork', {}, 'Utworzono fork – wdrożenie w Waszej instytucji.')}>
        ⑂ Wdrażamy u siebie (fork)
      </button>,
    )
  }
  if (viewer.canConfirmDeployment) {
    actions.push(
      <button key="deploy" className="btn btn-good" disabled={busy}
        onClick={() => act('/confirm-deployment', {}, 'Potwierdzono wdrożenie.')}>
        ✓ Potwierdź wdrożenie
      </button>,
    )
  }
  if (viewer.canContact) {
    actions.push(
      <button key="contact" className="btn" onClick={() => setContactOpen((o) => !o)}>
        ✉ Poproś o kontakt
      </button>,
    )
  }

  const hints = []
  if (!user) hints.push('Wybierz użytkownika demo w nagłówku, żeby głosować, forkować albo prosić o kontakt.')
  if (card.status.draft) hints.push('To zgłoszenie nie jest jeszcze publiczne – czeka na potwierdzenie autora lub opiekuna instytucji.')
  if (card.status.value === 'W_OCENIE' && !viewer.canApprove) hints.push('Propozycja oceny czeka na eksperta PCI.')
  if (viewer.canApprove) hints.push('Jako ekspert PCI sprawdź propozycję oceny poniżej i zatwierdź ją.')
  if (viewer.canChoosePaths && card.status.value === 'OCENIONA') hints.push('Ocena zatwierdzona – wybierz ścieżkę rozwoju w sekcji „Ścieżki”.')

  if (actions.length === 0 && hints.length === 0 && completeness.percent === 100) return null

  return (
    <div className="card action-panel">
      {completeness.percent < 100 && (
        <div className="completeness">
          <div className="progress"><div style={{ width: `${completeness.percent}%` }} /></div>
          <span className="small">
            Karta wypełniona w {completeness.percent}%. Brakuje: {completeness.missing.join(', ')}.
          </span>
        </div>
      )}
      {hints.map((h) => (
        <p key={h} className="small muted">{h}</p>
      ))}
      {actions.length > 0 && <div className="actions">{actions}</div>}
      {contactOpen && (
        <div className="contact-form">
          <textarea placeholder="Krótko: kim jesteś i czego szukasz (dane kontaktowe zobaczycie po akceptacji prośby)."
            value={message} onChange={(e) => setMessage(e.target.value)} />
          <button className="btn btn-primary" disabled={busy}
            onClick={async () => {
              const ok = await act('/contact', { message }, 'Wysłano prośbę o kontakt.')
              if (ok) setContactOpen(false)
            }}>
            Wyślij prośbę
          </button>
        </div>
      )}
    </div>
  )
}

function CardSection({ data }) {
  const { card } = data
  const rows = [
    ['Problem', data.problem],
    ['Rozwiązanie', data.solution],
    ['Wymagania wdrożenia', data.requirements],
    ['Efekty', data.results],
  ]
  return (
    <Section title="Karta innowacji" subtitle="Ustandaryzowany opis – jak README repozytorium">
      <dl className="readme">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value || <span className="muted">— brak, warto uzupełnić</span>}</dd>
          </div>
        ))}
      </dl>
      <div className="facts">
        <Fact label="Etap">{card.maturity?.label}</Fact>
        <Fact label="Model transferu">{card.transferModel?.label}</Fact>
        <Fact label="Ochrona IP">{data.ipStatus}</Fact>
        {data.reportedBy && <Fact label="Zgłosił(a)"><PersonLink person={data.reportedBy} /> (zwiadowca)</Fact>}
        {data.sourceRef && <Fact label="Źródło">{card.sourceName} · {data.sourceRef}</Fact>}
      </div>
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

function Fact({ label, children }) {
  if (!children) return null
  return (
    <div className="fact">
      <span className="muted small">{label}</span>
      <strong>{children}</strong>
    </div>
  )
}

/** Karta oceny z jawnymi wagami. Ekspert może poprawić każde kryterium przed zatwierdzeniem. */
function AssessmentSection({ data, busy, act }) {
  const { assessment, viewer, card } = data
  const [edits, setEdits] = useState({})

  if (!assessment) {
    return (
      <Section title="Ocena potencjału" subtitle="6 kryteriów, jawne wagi, uzasadnienie każdej oceny">
        <Empty>
          {card.status.step < 3
            ? 'Ocena będzie możliwa po potwierdzeniu i uzupełnieniu karty.'
            : 'Innowacja nie była jeszcze oceniana.'}
        </Empty>
      </Section>
    )
  }

  const editing = viewer.canApprove
  const total = editing
    ? assessment.scores.reduce((sum, s) => {
        const v = edits[s.criterion.value]?.score ?? s.proposedScore
        return sum + (s.criterion.weight / 100) * ((v - 1) / 4) * 100
      }, 0)
    : assessment.total

  return (
    <Section
      title="Ocena potencjału"
      subtitle={assessment.approved
        ? `Zatwierdzona przez ${assessment.approvedBy?.name ?? 'eksperta PCI'} · ${formatDate(assessment.approvedAt)}`
        : 'Propozycja automatyczna – czeka na weryfikację eksperta PCI'}
      actions={<ScoreRing score={total} size={64} />}
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
        Wynik = Σ waga × (ocena − 1) / 4 × 100. Głosy społeczności liczymy osobno, żeby popularność nie zagłuszyła jakości.
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

/** Rekomendowane ścieżki z regułą, uzasadnieniem, odbiorcami i checklistą. */
function PathsSection({ data, busy, act, version }) {
  const { card, viewer } = data
  const [recs, setRecs] = useState(null)
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    if (card.status.step < 3) return
    api.get(`/innovations/${card.id}/recommendations`).then(setRecs).catch(() => setRecs([]))
  }, [card.id, card.status.step, version])

  if (card.status.step < 3) return null
  const chosen = selected ?? new Set(data.chosenPaths.map((p) => p.value))
  const choosing = viewer.canChoosePaths

  return (
    <Section title="Ścieżki rozwoju" subtitle="Rekomendacje z jawnych reguł – ekspert PCI wybiera i zatwierdza">
      {data.chosenPaths.length > 0 && (
        <p>Zatwierdzona ścieżka: {data.chosenPaths.map((p) => <Badge key={p.value} tone="accent">{p.label}</Badge>)}</p>
      )}
      {!recs ? <Loading /> : recs.map((r) => (
        <article key={r.path} className={`path ${r.chosen ? 'path-chosen' : ''}`}>
          <header>
            {choosing && (
              <input type="checkbox" checked={chosen.has(r.path)} aria-label={`Wybierz ścieżkę ${r.label}`}
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
          <p className="small"><span className="muted">Do kogo:</span> {r.audience}</p>
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
          onClick={() => act('/paths', { paths: [...chosen] }, 'Zatwierdzono ścieżkę rozwoju.')}>
          Zatwierdź wybrane ścieżki
        </button>
      )}
    </Section>
  )
}

function targetLink(t) {
  if (t.kind === 'PARTNER') return `/people/${t.id}`
  if (t.kind === 'INNOWACJA') return `/innovations/${t.id}`
  return t.institutionId ? `/institutions/${t.institutionId}` : '/needs'
}

function MatchesSection({ id, version }) {
  const { data } = useFetch(`/innovations/${id}/matches`, [version])
  return (
    <Section title="Polecane połączenia" subtitle="Kto może to wdrożyć, rozwinąć lub sfinansować">
      {!data ? <Loading /> : (
        <>
          <h3 className="sub">Potrzeby z tablicy</h3>
          {data.needs.length === 0 ? <p className="muted small">Brak dopasowanych potrzeb.</p> : data.needs.map((m) => <MatchRow key={`n${m.id}`} m={m} />)}
          <h3 className="sub">Partnerzy i inwestorzy</h3>
          {data.partners.length === 0 ? <p className="muted small">Brak dopasowanych partnerów.</p> : data.partners.map((m) => <MatchRow key={`p${m.id}`} m={m} />)}
        </>
      )}
    </Section>
  )
}

function MatchRow({ m }) {
  return (
    <div className="match">
      <div className="match-head">
        <Link to={targetLink(m)}>{m.title}</Link>
        <Badge tone={m.score >= 70 ? 'good' : 'info'}>{m.score}%</Badge>
      </div>
      <div className="muted small">{m.subtitle}</div>
      <div className="reasons">{m.reasons.map((r) => <span key={r}>{r}</span>)}</div>
    </div>
  )
}

function SimilarSection({ id }) {
  const { data } = useFetch(`/innovations/${id}/similar`)
  return (
    <Section title="Podobne rozwiązania" subtitle="Ogranicza dublowanie prac i zasila kryterium „Nowość”">
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

function ForksSection({ forks, deployments }) {
  if (!forks.length) return null
  return (
    <Section title={`Wdrożenia (forki): ${forks.length}`} subtitle={`Potwierdzone: ${deployments}`}>
      <ul className="fork-list">
        {forks.map((f) => (
          <li key={f.id}>
            <span className="fork-icon">⑂</span>
            <div>
              <Link to={`/innovations/${f.id}`}>{f.institution?.name}</Link>
              <div><StatusBadge status={f.status} /> <span className="muted small">od {formatDate(f.createdAt)}</span></div>
            </div>
          </li>
        ))}
      </ul>
    </Section>
  )
}

function TimelineSection({ events }) {
  return (
    <Section title="Oś czasu" subtitle="Historia zmian – jak commity w repozytorium">
      <ol className="timeline">
        {[...events].reverse().map((e) => (
          <li key={e.id}>
            <span className="when">{formatDate(e.createdAt)}</span>
            <div>
              <div>{e.message}</div>
              <div className="muted small">
                {e.type.label}
                {e.actor && <> · <PersonLink person={e.actor} /></>}
              </div>
            </div>
          </li>
        ))}
      </ol>
    </Section>
  )
}
