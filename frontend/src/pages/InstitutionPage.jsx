import { Link, useParams } from 'react-router-dom'
import InnovationCard from '../components/InnovationCard.jsx'
import InstitutionLogo from '../components/InstitutionLogo.jsx'
import { Badge, Empty, ErrorBox, Loading, Section } from '../components/ui.jsx'
import { useFetch } from '../useFetch.js'

/** Strona instytucji: innowacje znalezione w jej źródłach i jej zapytania o rozwiązanie. */
export default function InstitutionPage() {
  const { id } = useParams()
  const { data, loading, error } = useFetch(`/institutions/${id}`)
  if (loading && !data) return <Loading />
  if (error) return <ErrorBox>{error}</ErrorBox>
  const { institution, innovations, needs } = data
  const own = innovations
  const verified = own.filter((c) => c.status.value === 'OCENIONA').length

  return (
    <>
      <section className="card profile-head">
        <InstitutionLogo institution={institution} size={96} />
        <div className="profile-main">
          <h1>{institution.name}</h1>
          <p className="muted">
            <Badge tone={institution.publicSector ? 'info' : 'warn'}>{institution.type.label}</Badge>{' '}
            {[institution.city, institution.county && `powiat ${institution.county}`, institution.region].filter(Boolean).join(' · ')}
          </p>
          <div className="inst-stats">
            <div><strong>{own.length}</strong><span>innowacji</span></div>
            <div><strong>{verified}</strong><span>zweryfikowanych przez PCI</span></div>
            <div><strong>{needs.length}</strong><span>zapytań o rozwiązanie</span></div>
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
        </div>
        <aside className="col-side">
          {needs.length > 0 && (
            <Section title="Zapytania o rozwiązanie" subtitle={<Link to="/needs">zobacz dopasowania</Link>}>
              <ul className="plain-list">
                {needs.map((n) => <li key={n.id}><strong>{n.title}</strong></li>)}
              </ul>
            </Section>
          )}
        </aside>
      </div>
    </>
  )
}
