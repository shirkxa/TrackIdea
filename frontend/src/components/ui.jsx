import { Link } from 'react-router-dom'

export function Loading({ label = 'Ładowanie…' }) {
  return <div className="loading">{label}</div>
}

export function ErrorBox({ children }) {
  return children ? <div className="error-box">{children}</div> : null
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>
}

export function Tag({ children }) {
  return <span className="tag">#{children}</span>
}

const STATUS_TONE = {
  WYKRYTA: 'info', W_OCENIE: 'warn', OCENIONA: 'accent', ODRZUCONA: 'muted',
  POLACZONA: 'muted', ARCHIWALNA: 'muted',
}

export function StatusBadge({ status }) {
  if (!status) return null
  return <span className={`badge badge-${STATUS_TONE[status.value] ?? 'info'}`}>{status.label}</span>
}

export function Badge({ children, tone = 'info', title }) {
  return <span className={`badge badge-${tone}`} title={title}>{children}</span>
}

export function ScoreRing({ score, size = 56, label = 'wynik', estimated = false }) {
  if (score === null || score === undefined) {
    return <div className="score-ring empty-score" style={{ width: size, height: size }} title="Jeszcze nie oceniono"><span>–</span></div>
  }
  const deg = Math.round((score / 100) * 360)
  const title = estimated
    ? `Ocena automatyczna ${Math.round(score)}/100 – szacunek, czeka na weryfikację analityka PCI`
    : `Ocena zweryfikowana przez analityka PCI: ${Math.round(score)}/100`
  return (
    <div className={`score-ring ${estimated ? 'score-estimated' : ''}`} style={{ width: size, height: size, '--deg': `${deg}deg` }}
      title={title} aria-label={title}>
      <span>{estimated ? '≈' : ''}{Math.round(score)}</span>
      {size >= 70 && <small>{estimated ? 'szacunek' : label}</small>}
    </div>
  )
}

/** 8 kroków cyklu życia innowacji (jak w Banku Pomysłów). */
export function Pipeline({ statuses, current }) {
  const steps = []
  const seen = new Set()
  statuses
    .filter((s) => s.step > 0)
    .forEach((s) => {
      if (!seen.has(s.step)) {
        seen.add(s.step)
        steps.push(s)
      }
    })
  const side = current && current.step === 0
  return (
    <ol className={`pipeline ${side ? 'pipeline-side' : ''}`}>
      {steps.map((s) => {
        const state = !current || side ? 'todo' : s.step < current.step ? 'done' : s.step === current.step ? 'now' : 'todo'
        return (
          <li key={s.step} className={`step step-${state}`} title={s.description}>
            <span className="dot">{state === 'done' ? '✓' : s.step}</span>
            <span className="step-label">{s.label}</span>
          </li>
        )
      })}
    </ol>
  )
}

export function InstitutionLink({ institution }) {
  if (!institution) return null
  return <Link to={`/institutions/${institution.id}`}>{institution.name}</Link>
}

export function Section({ title, subtitle, children, actions, id }) {
  return (
    <section className="section" id={id}>
      <header className="section-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
        {actions && <div className="section-actions">{actions}</div>}
      </header>
      {children}
    </section>
  )
}

export function SelectEnum({ label, value, onChange, options, placeholder = 'Wszystkie', compact = false }) {
  const select = (
    <select value={value ?? ''} onChange={(e) => onChange(e.target.value || null)} aria-label={label}>
      <option value="">{compact ? label : placeholder}</option>
      {options?.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
  if (compact) return select
  return (
    <label className="field">
      <span>{label}</span>
      {select}
    </label>
  )
}
