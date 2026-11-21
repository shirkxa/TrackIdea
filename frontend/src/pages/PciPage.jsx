import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { Badge, Empty, ErrorBox, Loading, Section, StatusBadge } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { formatDate } from '../format.js'
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
      notify(r.created + r.duplicates === 0
        ? 'Brak nowych rekordów w źródłach.'
        : `Radar: nowe innowacje ${r.created}, duplikaty ${r.duplicates}.`)
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
          <h1>{isPci ? 'Panel PCI' : 'Radar i mierniki'}</h1>
          <p className="muted">
            Jak skutecznie TrackIdea odnajduje ukryte innowacje i przenosi je do kolejnych instytucji.
            {!isPci && ' Wybierz eksperta PCI w nagłówku, żeby zobaczyć kolejkę ocen i uruchomić Radar.'}
          </p>
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
                {k.target !== '–' && <span className="kpi-target">cel pilotażu: {k.target}</span>}
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
        subtitle="Automatyczne wykrywanie innowacji w otwartych bazach. Każdy wykryty rekord trafia do autora lub opiekuna instytucji do potwierdzenia."
        actions={isPci && (
          <button className="btn btn-primary" disabled={scanning} onClick={scan}>
            {scanning ? 'Skanowanie…' : '◎ Uruchom skan Radaru'}
          </button>
        )}
      >
        {radar.data && (
          <ul className="sources">
            {radar.data.sources.map((s) => <li key={s}>{s}</li>)}
          </ul>
        )}
        <p className="muted small">
          Przetwarzanie: import → ekstrakcja pól → wyszukanie podobnych (duplikat od 75%) → przypisanie instytucji → status „Wykryta”.
          W prototypie Radar czyta próbki eksportów z plików JSON; docelowo każde źródło to osobny importer.
        </p>
        {lastScan && (
          <p className="notice small">
            Ostatni skan: przejrzane rekordy {lastScan.scanned}, nowe innowacje {lastScan.created},
            duplikaty {lastScan.duplicates}, już zaimportowane {lastScan.skipped}.
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
    ['toApprove', 'Oceny do zatwierdzenia', 'Propozycja oceny czeka na Ciebie'],
    ['toChoosePath', 'Ocenione bez ścieżki', 'Wskaż ścieżkę rozwoju'],
    ['drafts', 'Do potwierdzenia przez autorów', 'Wykryte przez Radar lub zgłoszone przez zwiadowców'],
    ['readyForAssessment', 'Gotowe do oceny', 'Karta kompletna – autor może poprosić o ocenę'],
  ]
  return (
    <Section title="Kolejka pracy" subtitle="Co czeka na eksperta PCI – nic nie utknie bez właściciela">
      {!queue.data ? <Loading /> : (
        <div className="queue">
          {groups.map(([key, title, hint]) => (
            <div key={key} className="queue-col">
              <h3>{title} <Badge tone={queue.data[key].length > 0 && key === 'toApprove' ? 'warn' : 'muted'}>{queue.data[key].length}</Badge></h3>
              <p className="muted small">{hint}</p>
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
