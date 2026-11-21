import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { Badge, Empty, ErrorBox, Loading, Section, StatusBadge } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { formatDate, formatDateTime } from '../format.js'
import { useFetch } from '../useFetch.js'

/** Panel PCI: mierniki skuteczności, kolejka pracy eksperta i Radar (automatyczne wykrywanie). */
export default function PciPage() {
  const { user, notify } = useApp()
  const isPci = Boolean(user && ['EKSPERT', 'ADMIN'].includes(user.role.value))
  const [version, setVersion] = useState(0)
  const stats = useFetch('/stats', [version])
  const radar = useFetch('/radar', [version])
  const queue = useFetch(isPci ? '/pci/queue' : null, [version])
  const [scanning, setScanning] = useState(false)
  const [lastScan, setLastScan] = useState(null)

  async function scan() {
    setScanning(true)
    try {
      const r = await api.post('/radar/scan')
      setLastScan(r)
      setVersion((v) => v + 1)
      notify(r.errors?.length
        ? `Część źródeł niedostępna: ${r.errors.join('; ')}`
        : r.created + r.duplicates + r.updated === 0
          ? 'Źródła sprawdzone – brak nowych rekordów.'
          : `Radar: nowe ${r.created}, zaktualizowane ${r.updated}, duplikaty ${r.duplicates}.`,
      r.errors?.length ? 'error' : 'ok')
    } catch (e) {
      notify(e.message, 'error')
    } finally {
      setScanning(false)
    }
  }

  return (
    <>
      <section className="page-head">
        <div>
          <h1 className="page-title">{isPci ? 'Panel PCI' : 'Radar i mierniki'}</h1>
        </div>
      </section>

      <ErrorBox>{stats.error}</ErrorBox>
      {!stats.data ? <Loading /> : (
        <>
          <div className="kpis">
            {stats.data.kpis.map((k) => (
              <div key={k.key} className="kpi card">
                <span className="kpi-label">{k.label}</span>
                <strong className="kpi-value">{k.value}</strong>
                <span className="muted small">{k.hint}</span>
                {k.target !== '–' && <span className="kpi-target">cel: {k.target}</span>}
              </div>
            ))}
          </div>

          <div className="dist-grid">
            <Distribution title="Innowacje wg statusu" data={stats.data.byStatus} />
            <Distribution title="Wg sektora" data={stats.data.bySector} />
            <Distribution title="Wg typu instytucji" data={stats.data.byInstitutionType} />
            <Distribution title="Skąd pochodzą" data={stats.data.bySource} />
          </div>
        </>
      )}

      {isPci && <QueueSection queue={queue} />}

      <Section
        title="Radar"
        actions={isPci && (
          <button className="btn btn-primary" disabled={scanning} onClick={scan}>
            {scanning ? 'Skanowanie źródeł…' : '◎ Skanuj teraz'}
          </button>
        )}
      >
        {radar.data && (
          <>
            <p className="muted small">
              {radar.data.live ? 'Tryb online' : 'Tryb offline (próbki z plików)'}
              {radar.data.lastScanAt && <> · ostatni skan {formatDateTime(radar.data.lastScanAt)}</>}
              {radar.data.nextScanAt && <> · następny automatycznie {formatDateTime(radar.data.nextScanAt)}</>}
            </p>
            <div className="source-grid">
              {radar.data.sources.filter((s) => s.active).map((s) => (
                <div key={s.name} className={`source-card ${s.lastError ? 'source-error' : ''}`}>
                  <div className="source-head">
                    <strong>{s.homepage ? <a href={s.homepage} target="_blank" rel="noreferrer">{s.name}</a> : s.name}</strong>
                    <Badge tone={s.lastError ? 'warn' : s.lastRunAt ? 'good' : 'muted'}>
                      {s.lastError ? 'błąd' : s.lastRunAt ? 'działa' : 'czeka na skan'}
                    </Badge>
                  </div>
                  <p className="muted small">{s.description}</p>
                  <p className="small">
                    W bazie: <b>{s.totalRecords}</b>
                    {s.lastRunAt && <> · ostatnio pobrano {s.lastFetched}, nowe {s.lastCreated}, zaktualizowane {s.lastUpdated}</>}
                  </p>
                  {s.lastError && <p className="small">{s.lastError}</p>}
                </div>
              ))}
            </div>
          </>
        )}
        {lastScan && (
          <p className="notice small">
            Skan: przejrzane {lastScan.scanned}, nowe {lastScan.created}, zaktualizowane {lastScan.updated},
            duplikaty {lastScan.duplicates}, bez zmian {lastScan.skipped}.
          </p>
        )}
        {!radar.data ? <Loading /> : radar.data.records.length === 0 ? (
          <Empty>Radar nie przetworzył jeszcze żadnych rekordów.</Empty>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Źródło</th>
                  <th>Rekord</th>
                  <th>Instytucja</th>
                  <th>Wynik</th>
                  <th>Data</th>
                </tr>
              </thead>
              <tbody>
                {radar.data.records.map((r) => (
                  <tr key={r.id}>
                    <td><Badge tone="radar">{r.source}</Badge><div className="muted small">{r.ref}</div></td>
                    <td>{r.title}</td>
                    <td className="small">{r.institutionName}</td>
                    <td>
                      {r.outcome === 'DUPLIKAT' ? (
                        <>
                          <Badge tone="warn">Duplikat · {Math.round(r.duplicateSimilarity)}%</Badge>
                          <div className="small">
                            istnieje: <Link to={`/innovations/${r.duplicateOf.id}`}>{r.duplicateOf.name}</Link>
                          </div>
                        </>
                      ) : (
                        <>
                          <Badge tone="good">Nowa innowacja</Badge>
                          {r.innovation && (
                            <div className="small"><Link to={`/innovations/${r.innovation.id}`}>otwórz kartę</Link></div>
                          )}
                        </>
                      )}
                    </td>
                    <td className="small muted">{formatDate(r.importedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Section>
    </>
  )
}

/** Poziomy wykres słupkowy jednej miary (jeden kolor, wartości przy słupkach). */
function Distribution({ title, data }) {
  const entries = Object.entries(data ?? {}).sort((a, b) => b[1] - a[1])
  const max = Math.max(1, ...entries.map(([, v]) => v))
  const total = entries.reduce((s, [, v]) => s + v, 0)
  return (
    <div className="card dist">
      <h3>{title}</h3>
      {entries.length === 0 ? <p className="muted small">Brak danych.</p> : (
        <ul className="bars" aria-label={title}>
          {entries.map(([label, value]) => (
            <li key={label} title={`${label}: ${value} (${Math.round((100 * value) / total)}%)`}>
              <span className="bar-label">{label}</span>
              <span className="bar-track">
                <span className="bar-fill" style={{ width: `${(100 * value) / max}%` }} />
              </span>
              <span className="bar-value">{value}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function QueueSection({ queue }) {
  const groups = [
    ['toApprove', 'Oceny do zatwierdzenia'],
    ['toReview', 'Nowe z Radaru – do weryfikacji'],
  ]
  return (
    <Section title="Kolejka pracy">
      {!queue.data ? <Loading /> : (
        <div className="queue">
          {groups.map(([key, title]) => (
            <div key={key} className="queue-col">
              <h3>{title} <Badge tone={queue.data[key].length > 0 && key === 'toApprove' ? 'warn' : 'muted'}>{queue.data[key].length}</Badge></h3>
              {queue.data[key].length === 0 ? <p className="muted small">Pusto.</p> : (
                <ul className="queue-list">
                  {queue.data[key].map((c) => (
                    <li key={c.id}>
                      <Link to={`/innovations/${c.id}`}>{c.title}</Link>
                      <div className="small muted">
                        <StatusBadge status={c.status} /> {c.institution?.name}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      )}
    </Section>
  )
}
