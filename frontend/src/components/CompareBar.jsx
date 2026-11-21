import { Link, useLocation } from 'react-router-dom'
import { COMPARE_MAX, useApp } from '../context.jsx'

/** Pasek u dołu ekranu, gdy wybrano innowacje do porównania. */
export default function CompareBar() {
  const { compare, clearCompare } = useApp()
  const { pathname } = useLocation()
  if (compare.length === 0 || pathname === '/compare') return null
  return (
    <div className="compare-bar" role="region" aria-label="Porównanie">
      <span>Do porównania: <b>{compare.length}</b>/{COMPARE_MAX}</span>
      {compare.length < 2 && <span className="muted small">wybierz jeszcze co najmniej jedną</span>}
      <Link className={`btn btn-primary btn-small ${compare.length < 2 ? 'disabled' : ''}`} to="/compare"
        aria-disabled={compare.length < 2} onClick={(e) => compare.length < 2 && e.preventDefault()}>
        Porównaj
      </Link>
      <button type="button" className="link-button" onClick={clearCompare}>Wyczyść</button>
    </div>
  )
}
