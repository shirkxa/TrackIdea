import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api.js'
import { Badge, Empty, ErrorBox, Loading, StatusBadge } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { clearCompare, toggleCompare, useCompareIds } from '../compareStore.js'

/** Porównanie 2–4 innowacji obok siebie: klasyfikacja, TRL, ocena wg kryteriów i fakty ze źródła. */
export default function ComparePage() {
  const { meta } = useApp()
  const compare = useCompareIds()
  const [params, setParams] = useSearchParams()
  const fromUrl = params.get('ids')
  const ids = fromUrl ? fromUrl.split(',').map(Number).filter(Number.isInteger).slice(0, 4) : compare
  const key = ids.join(',')
  const [result, setResult] = useState({ key: null, items: null, error: null })

  useEffect(() => {
    if (!key) return
    // jedno zapytanie na wszystkie karty; karty usunięte z bazy (np. po restarcie demo) backend pomija
    const ctrl = new AbortController()
    api.get(`/innovations/compare?ids=${key}`, ctrl.signal)
      .then((items) => setResult(items.length > 0
        ? { key, items, error: null }
        : { key, items: null, error: 'Nie udało się wczytać wybranych innowacji.' }))
      .catch((e) => {
        if (e.name !== 'AbortError') setResult({ key, items: null, error: e.message })
      })
    return () => ctrl.abort()
  }, [key])

  const remove = (id) => {
    if (fromUrl) setParams(ids.length > 1 ? { ids: ids.filter((x) => x !== id).join(',') } : {})
    else toggleCompare(id)
  }

  if (ids.length === 0) {
    return (
      <>
        <h1 className="page-title">Porównanie</h1>
        <Empty>Wybierz innowacje w <Link to="/">katalogu</Link> przyciskiem „+ Porównaj”.</Empty>
      </>
    )
  }
  if (result.error) return <ErrorBox>{result.error}</ErrorBox>
  if (result.key !== key || !result.items) return <Loading />

  const items = result.items
  const shareUrl = `${window.location.origin}/compare?ids=${key}`
  const est = (d, field) => d.card.estimated?.includes(field)
  const score = (d, criterion) => {
    const s = d.assessment?.scores.find((x) => x.criterion.value === criterion)
    return s ? s.finalScore ?? s.proposedScore : null
  }

  const rows = [
    { label: 'Instytucja', render: (d) => d.card.institution?.name },
    { label: 'Obszar', render: (d) => d.card.area?.label, estimated: (d) => est(d, 'area') },
    { label: 'Typ', render: (d) => d.card.type?.label, estimated: (d) => est(d, 'type') },
    { label: 'Sektor', render: (d) => d.card.sector?.label, estimated: (d) => est(d, 'sector') },
    { label: 'Etap wdrożenia', render: (d) => d.card.maturity?.label, estimated: (d) => est(d, 'maturity') },
    {
      label: 'Gotowość (TRL)', value: (d) => d.card.trl?.level,
      render: (d) => d.card.trl && <>TRL {d.card.trl.level}<div className="muted small">{d.card.trl.label}</div></>,
      estimated: (d) => est(d, 'trl'),
    },
    { label: 'Status', render: (d) => <StatusBadge status={d.card.status} /> },
    {
      label: 'Wynik oceny', value: (d) => (d.assessment ? Math.round(d.assessment.approved ? d.assessment.total : d.assessment.proposedTotal) : null),
      render: (d) => d.assessment
        ? <><b>{Math.round(d.assessment.approved ? d.assessment.total : d.assessment.proposedTotal)}</b>/100
          {!d.assessment.approved && <div className="muted small">propozycja – niezatwierdzona</div>}</>
        : <span className="muted">nieoceniona</span>,
    },
    ...meta.criteria.map((c) => ({
      label: c.label, sub: `waga ${c.weight}%`, value: (d) => score(d, c.value),
      render: (d) => (score(d, c.value) == null ? <span className="muted">–</span> : <>{score(d, c.value)}/5</>),
    })),
    { label: 'Źródło', render: (d) => [d.card.sourceName, d.sourceRef].filter(Boolean).join(' · ') },
    {
      label: 'Fakty ze źródła',
      render: (d) => d.sourceFacts?.length > 0 && (
        <ul className="compare-facts">{d.sourceFacts.filter((f) => !f.startsWith('Polska')).map((f) => <li key={f}>{f}</li>)}</ul>
      ),
    },
    {
      label: 'Link', render: (d) => d.links?.length > 0 && (
        <a href={[...d.links][0]} target="_blank" rel="noreferrer">Zobacz w źródle ↗</a>
      ),
    },
  ]

  return (
    <>
      <section className="page-head">
        <h1 className="page-title">Porównanie</h1>
        <div className="actions">
          <button type="button" className="btn btn-small" onClick={() => navigator.clipboard?.writeText(shareUrl)}
            title={shareUrl}>Kopiuj link</button>
          {!fromUrl && <button type="button" className="btn btn-small" onClick={clearCompare}>Wyczyść</button>}
        </div>
      </section>
      <p className="muted small">
        Najwyższe wartości są wyróżnione. <Badge tone="estimate">szacunek</Badge> = ustalone automatycznie, do potwierdzenia.
      </p>
      <div className="table-wrap">
        <table className="table compare-table">
          <thead>
            <tr>
              <th scope="col"><span className="sr-only">Cecha</span></th>
              {items.map((d) => (
                <th key={d.card.id} scope="col">
                  <Link to={`/innovations/${d.card.id}`}>{d.card.title}</Link>
                  <button type="button" className="link-button remove" onClick={() => remove(d.card.id)}
                    aria-label={`Usuń z porównania: ${d.card.title}`}>✕ usuń</button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const values = r.value ? items.map(r.value).filter((v) => v != null) : []
              const best = values.length > 1 && new Set(values).size > 1 ? Math.max(...values) : null
              return (
                <tr key={r.label}>
                  <th scope="row">{r.label}{r.sub && <div className="muted small">{r.sub}</div>}</th>
                  {items.map((d) => (
                    <td key={d.card.id} className={best != null && r.value(d) === best ? 'best' : ''}>
                      {r.render(d) || <span className="muted">–</span>}
                      {r.estimated?.(d) && <> <Badge tone="estimate">szacunek</Badge></>}
                    </td>
                  ))}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </>
  )
}
