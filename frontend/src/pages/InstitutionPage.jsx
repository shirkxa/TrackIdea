import { Link, useParams } from 'react-router-dom'
import InnovationCard from '../components/InnovationCard.jsx'
import { Badge, Empty, ErrorBox, Loading, Section } from '../components/ui.jsx'
import { useFetch } from '../useFetch.js'

/** Strona instytucji: jej innowacje, wdrożenia cudzych rozwiązań (forki), potrzeby i ludzie. */
export default function InstitutionPage() {
  const { id } = useParams()
  const { data, loading, error } = useFetch(`/institutions/${id}`)
  if (loading && !data) return <Loading />
  if (error) return <ErrorBox>{error}</ErrorBox>
  const { institution, people, innovations, needs } = data
  const own = innovations.filter((c) => !c.forkedFrom)
  const forks = innovations.filter((c) => c.forkedFrom)
  const deployedElsewhere = own.reduce((sum, c) => sum + c.deployments, 0)

  return (
    <>
      <section className="card profile-head">
        <span className="avatar avatar-big avatar-inst">{institution.name.replace(/[^A-ZĄĆĘŁŃÓŚŹŻ]/g, '').slice(0, 2)}</span>
        <div className="profile-main">
          <h1>{institution.name}</h1>
          <p className="muted">
            <Badge tone={institution.publicSector ? 'info' : 'warn'}>{institution.type.label}</Badge>{' '}
            {[institution.city, institution.county && `powiat ${institution.county}`, institution.region].filter(Boolean).join(' · ')}
          </p>
          <div className="inst-stats">
            <div><strong>{own.length}</strong><span>innowacji</span></div>
            <div><strong>{deployedElsewhere}</strong><span>wdrożeń w innych instytucjach</span></div>
            <div><strong>{forks.length}</strong><span>przejętych rozwiązań (forki)</span></div>
            <div><strong>{needs.length}</strong><span>potrzeb na tablicy</span></div>
          </div>
        </div>
      </section>

      <div className="two-col">
        <div className="col-main">
          <Section title="Innowacje">
            {own.length === 0 ? <Empty>Brak opublikowanych innowacji.</Empty> : (
              <div className="grid">{own.map((c) => <InnovationCard key={c.id} card={c} />)}</div>
            )}
          </Section>
          {forks.length > 0 && (
            <Section title="Wdrożone u nas (forki)" subtitle="Rozwiązania innych instytucji przeniesione tutaj">
              <div className="grid">{forks.map((c) => <InnovationCard key={c.id} card={c} />)}</div>
            </Section>
          )}
        </div>
        <aside className="col-side">
          {needs.length > 0 && (
            <Section title="Potrzeby" subtitle={<Link to="/needs">zobacz dopasowania na tablicy</Link>}>
              <ul className="plain-list">
                {needs.map((n) => <li key={n.id}><strong>{n.title}</strong></li>)}
              </ul>
            </Section>
          )}
          <Section title="Ludzie">
            {people.length === 0 ? <p className="muted small">Brak profili.</p> : (
              <ul className="plain-list">
                {people.map((p) => (
                  <li key={p.id}>
                    <Link to={`/people/${p.id}`}>{p.name}</Link>
                    <div className="muted small">{p.role.label}{p.headline && ` · ${p.headline}`}</div>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </aside>
      </div>
    </>
  )
}
