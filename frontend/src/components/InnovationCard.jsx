import { Link } from 'react-router-dom'
import { COMPARE_MAX, useApp } from '../context.jsx'
import { formatDate } from '../format.js'
import InstitutionLogo from './InstitutionLogo.jsx'
import { Badge, ScoreRing, StatusBadge, Tag } from './ui.jsx'

export default function InnovationCard({ card, extra, index = 0 }) {
  const { compare, toggleCompare } = useApp()
  const picked = compare.includes(card.id)
  const full = !picked && compare.length >= COMPARE_MAX
  return (
    <article className="card innovation-card pop" style={{ '--i': Math.min(index, 12) }}>
      <div className="card-top">
        <InstitutionLogo institution={card.institution} sector={card.sector} />
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
        {card.area && <Badge tone="muted">{card.area.label}</Badge>}
        {card.maturity && <Badge tone="muted">{card.maturity.label}</Badge>}
        {card.trl && (
          <Badge tone="trl" title={`${card.trl.label}${card.estimated?.includes('trl') ? ' (szacunek)' : ''}`}>
            TRL {card.trl.level}{card.estimated?.includes('trl') ? '*' : ''}
          </Badge>
        )}
      </div>
      <p className="card-summary">{card.summary}</p>
      <div className="tags">
        {[...(card.tags ?? [])].slice(0, 4).map((t) => (
          <Tag key={t}>{t}</Tag>
        ))}
      </div>
      <footer className="card-foot">
        <span className="card-date" title="Data dodania do Radaru">{formatDate(card.createdAt)}</span>
        {card.sourceName && <Badge tone="radar" title={card.source?.label}>{card.sourceName}</Badge>}
        <button type="button" className={`compare-toggle ${picked ? 'on' : ''}`} aria-pressed={picked}
          disabled={full} onClick={() => toggleCompare(card.id)}
          title={full ? `Można porównać najwyżej ${COMPARE_MAX} innowacje` : 'Dodaj do porównania'}>
          {picked ? '✓ W porównaniu' : '+ Porównaj'}
        </button>
      </footer>
      {extra}
    </article>
  )
}
