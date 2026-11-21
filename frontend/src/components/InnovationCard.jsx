import { Link } from 'react-router-dom'
import { Badge, ScoreRing, StatusBadge, Tag } from './ui.jsx'

export default function InnovationCard({ card, extra, index = 0 }) {
  return (
    <article className="card innovation-card pop" style={{ '--i': Math.min(index, 12) }}>
      <div className="card-top">
        <div className="card-badges">
          <StatusBadge status={card.status} />
          {card.type && <Badge tone="muted">{card.type.label}</Badge>}
          {card.source?.value === 'RADAR' && <Badge tone="radar" title="Wykryta automatycznie">Radar · {card.sourceName}</Badge>}
          {card.forkedFrom && <Badge tone="good">Fork</Badge>}
        </div>
        <ScoreRing score={card.score} size={44} />
      </div>
      <h3>
        <Link to={`/innovations/${card.id}`}>{card.title}</Link>
      </h3>
      <p className="muted small">{card.institution?.name}</p>
      <p className="card-summary">{card.summary}</p>
      <div className="tags">
        {[...(card.tags ?? [])].slice(0, 4).map((t) => (
          <Tag key={t}>{t}</Tag>
        ))}
      </div>
      <footer className="card-foot">
        <span title="Głosy „przydałoby się u nas”">▲ {card.votes}</span>
        <span title="Obserwujący">☆ {card.followers}</span>
        <span title="Forki / potwierdzone wdrożenia">⑂ {card.forks}{card.deployments > 0 ? ` · ✓${card.deployments}` : ''}</span>
        {card.sector && <span className="muted">{card.sector.label}</span>}
      </footer>
      {extra}
    </article>
  )
}
