import { useParams } from 'react-router-dom'
import InnovationCard from '../components/InnovationCard.jsx'
import { Badge, Empty, ErrorBox, InstitutionLink, Loading, Section, Tag } from '../components/ui.jsx'
import { useFetch } from '../useFetch.js'

/** Profil w stylu LinkedIna: kim jest, co zrobił(a), czego szuka, linki do GitHuba / YouTube / LinkedIna. */
export default function ProfilePage() {
  const { id } = useParams()
  const { data, loading, error } = useFetch(`/people/${id}`)
  if (loading && !data) return <Loading />
  if (error) return <ErrorBox>{error}</ErrorBox>
  const { person, innovations, needs } = data
  const initials = person.name.split(' ').filter((w) => !w.endsWith('.')).map((w) => w[0]).slice(0, 2).join('')
  const socials = [
    ['GitHub', person.github],
    ['YouTube', person.youtube],
    ['LinkedIn', person.linkedin],
  ].filter(([, url]) => url)
  const isPartner = person.role.value === 'PARTNER'

  return (
    <>
      <section className="card profile-head">
        <span className="avatar avatar-big">{initials}</span>
        <div className="profile-main">
          <h1>{person.name}</h1>
          <p className="lead">{person.headline}</p>
          <p className="muted small">
            <Badge tone="info">{person.role.label}</Badge>{' '}
            <InstitutionLink institution={person.institution} />
            {person.institution?.city && <> · {person.institution.city}</>}
          </p>
          {person.bio && <p>{person.bio}</p>}
          {socials.length > 0 && (
            <div className="socials">
              {socials.map(([label, url]) => (
                <a key={label} className="btn btn-small" href={url} target="_blank" rel="noreferrer">{label} ↗</a>
              ))}
            </div>
          )}
        </div>
      </section>

      {isPartner && (
        <Section title="Czego szuka" subtitle="Na tej podstawie TrackIdea poleca innowacje (polecajki)">
          <div className="facts">
            <div className="fact">
              <span className="muted small">Sektory</span>
              <strong>{person.interestSectors.map((s) => s.label).join(', ') || '—'}</strong>
            </div>
            <div className="fact">
              <span className="muted small">Etapy dojrzałości</span>
              <strong>{person.seekingStages.map((s) => s.label).join(', ') || 'dowolne'}</strong>
            </div>
          </div>
          <div className="tags">{[...(person.interestTags ?? [])].map((t) => <Tag key={t}>{t}</Tag>)}</div>
        </Section>
      )}

      <Section title={`Innowacje: ${innovations.length}`} subtitle="Opublikowane innowacje, których jest autorem lub autorką">
        {innovations.length === 0 ? <Empty>Brak opublikowanych innowacji.</Empty> : (
          <div className="grid">{innovations.map((c) => <InnovationCard key={c.id} card={c} />)}</div>
        )}
      </Section>

      {needs.length > 0 && (
        <Section title="Opublikowane potrzeby">
          <ul className="plain-list">
            {needs.map((n) => (
              <li key={n.id}>
                <strong>{n.title}</strong>
                <div className="muted small">{n.description}</div>
              </li>
            ))}
          </ul>
        </Section>
      )}
    </>
  )
}
