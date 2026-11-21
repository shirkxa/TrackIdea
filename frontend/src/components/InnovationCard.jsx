import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { useApp } from '../context.jsx'
import { formatDate } from '../format.js'
import SectorAvatar from './SectorAvatar.jsx'
import { Badge, ScoreRing, StatusBadge, Tag } from './ui.jsx'

export default function InnovationCard({ card, extra, index = 0 }) {
  const { user, notify } = useApp()
  const [social, setSocial] = useState({
    voted: card.voted, following: card.following, votes: card.votes, followers: card.followers,
  })
  const [busy, setBusy] = useState(false)

  async function toggle(kind) {
    if (!user) {
      notify('Wybierz użytkownika w prawym górnym rogu.', 'error')
      return
    }
    setBusy(true)
    try {
      const res = await api.post(`/innovations/${card.id}/${kind}`)
      setSocial((s) => kind === 'vote'
        ? { ...s, voted: res.voted, votes: s.votes + (res.voted ? 1 : -1) }
        : { ...s, following: res.following, followers: s.followers + (res.following ? 1 : -1) })
    } catch (e) {
      notify(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="card innovation-card pop" style={{ '--i': Math.min(index, 12) }}>
      <div className="card-top">
        <SectorAvatar sector={card.sector} />
        <div className="card-title">
          <h3>
            <Link to={`/innovations/${card.id}`}>{card.title}</Link>
          </h3>
          <p className="muted small">{card.institution?.name}</p>
        </div>
        <ScoreRing score={card.score} size={42} />
      </div>
      <div className="card-badges">
        <StatusBadge status={card.status} />
        {card.type && <Badge tone="muted">{card.type.label}</Badge>}
        {card.source?.value === 'RADAR' && <Badge tone="radar">Radar · {card.sourceName}</Badge>}
        {card.forkedFrom && <Badge tone="good">Fork</Badge>}
      </div>
      <p className="card-summary">{card.summary}</p>
      <div className="tags">
        {[...(card.tags ?? [])].slice(0, 4).map((t) => (
          <Tag key={t}>{t}</Tag>
        ))}
      </div>
      <footer className="card-foot">
        <span className="card-date" title="Data dodania">{formatDate(card.createdAt)}</span>
        {card.forks > 0 && <span title="Wdrożenia w innych instytucjach (forki)">⑂ {card.forks}</span>}
        {!card.status.draft && (
          <span className="card-actions">
            <button type="button" className={`chip ${social.voted ? 'chip-on' : ''}`} disabled={busy}
              onClick={() => toggle('vote')} aria-pressed={social.voted} title="Przydałoby się u nas">
              ▲ {social.votes}
            </button>
            <button type="button" className={`chip ${social.following ? 'chip-on' : ''}`} disabled={busy}
              onClick={() => toggle('follow')} aria-pressed={social.following}
              title={social.following ? 'Obserwujesz' : 'Obserwuj'}>
              {social.following ? '★' : '☆'} {social.followers}
            </button>
          </span>
        )}
      </footer>
      {extra}
    </article>
  )
}
