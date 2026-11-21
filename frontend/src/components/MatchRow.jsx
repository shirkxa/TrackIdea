import { Link } from 'react-router-dom'
import { Badge } from './ui.jsx'
import { targetLink } from '../links.js'

/** Jedno dopasowanie z wynikiem i powodami („polecane, bo…”). */
export default function MatchRow({ m }) {
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
