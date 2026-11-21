import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import InnovationCard from '../components/InnovationCard.jsx'
import MatchRow from '../components/MatchRow.jsx'
import { Badge, Empty, Loading, Section } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { formatDate } from '../format.js'
import { useFetch } from '../useFetch.js'

const CONTACT_TONE = { WYSLANA: 'warn', ZAAKCEPTOWANA: 'good', ODRZUCONA: 'muted' }

/** „Moje”: szkice do potwierdzenia, moje innowacje, prośby o kontakt i polecajki. */
export default function MePage() {
  const { user } = useApp()
  if (!user) {
    return <p className="notice"><Link to="/login">Zaloguj się</Link>, żeby zobaczyć swoje innowacje i kontakty.</p>
  }
  return <MyDashboard key={user.id} user={user} />
}

function MyDashboard({ user }) {
  const mine = useFetch('/innovations?scope=mine')
  const contacts = useFetch('/contacts')
  const recs = useFetch(`/people/${user.id}/recommendations`)

  const drafts = (mine.data ?? []).filter((c) => c.status.draft)
  const published = (mine.data ?? []).filter((c) => !c.status.draft)
  const showRecs = ['PARTNER', 'OPIEKUN'].includes(user.role.value)

  return (
    <>
      <section className="page-head">
        <div>
          <h1 className="page-title">Cześć, {user.name.split(' ').filter((w) => !w.endsWith('.'))[0]}</h1>
          <p className="muted">
            {user.role.label}{user.institution && <> · {user.institution.name}</>} ·{' '}
            <Link to={`/people/${user.id}`}>zobacz swój profil</Link>
          </p>
        </div>
        <Link className="btn btn-primary" to="/submit">Zgłoś innowację</Link>
      </section>

      {drafts.length > 0 && (
        <Section title="Czekają na potwierdzenie">
          <div className="grid">{drafts.map((c) => <InnovationCard key={c.id} card={c} />)}</div>
        </Section>
      )}

      <ContactsSection user={user} contacts={contacts} />

      {showRecs && (
        <Section
          title="Polecane dla Ciebie"
        >
          {!recs.data ? <Loading /> : recs.data.length === 0 ? (
            <Empty>Brak poleceń. {user.role.value === 'OPIEKUN' && <>Opisz problem na <Link to="/needs">tablicy potrzeb</Link>, a dopasujemy innowacje.</>}</Empty>
          ) : recs.data.map((m) => <MatchRow key={m.id} m={m} />)}
        </Section>
      )}

      <Section title="Moje innowacje">
        {!mine.data ? <Loading /> : published.length === 0 ? (
          <Empty>Nie masz jeszcze opublikowanych innowacji. <Link to="/submit">Zgłoś pierwszą</Link>.</Empty>
        ) : (
          <div className="grid">{published.map((c) => <InnovationCard key={c.id} card={c} />)}</div>
        )}
      </Section>
    </>
  )
}

/** Prośby o kontakt – dane kontaktowe są widoczne dopiero po akceptacji (RODO). */
function ContactsSection({ user, contacts }) {
  const { notify } = useApp()
  const [busy, setBusy] = useState(null)
  const list = contacts.data ?? []
  const incoming = list.filter((c) => c.to.id === user.id)
  const outgoing = list.filter((c) => c.from.id === user.id)

  async function answer(id, decision) {
    setBusy(id)
    try {
      const updated = await api.post(`/contacts/${id}/${decision}`)
      contacts.setData((l) => l.map((c) => (c.id === id ? updated : c)))
      notify(decision === 'accept' ? 'Zaakceptowano – widzicie teraz swoje dane kontaktowe.' : 'Odrzucono prośbę.')
    } catch (e) {
      notify(e.message, 'error')
    } finally {
      setBusy(null)
    }
  }

  if (contacts.data && list.length === 0) return null
  return (
    <Section title="Prośby o kontakt">
      {!contacts.data ? <Loading /> : (
        <div className="contacts">
          {incoming.length > 0 && <h3 className="sub">Do Ciebie</h3>}
          {incoming.map((c) => (
            <article key={c.id} className="contact">
              <div className="contact-head">
                <div>
                  <strong><Link to={`/people/${c.from.id}`}>{c.from.name}</Link></strong>
                  <span className="muted small"> · {c.from.role.label}{c.from.institution && `, ${c.from.institution.name}`}</span>
                </div>
                <Badge tone={CONTACT_TONE[c.status.value]}>{c.status.label}</Badge>
              </div>
              <div className="small muted">w sprawie: <Link to={`/innovations/${c.innovation.id}`}>{c.innovation.name}</Link> · {formatDate(c.createdAt)}</div>
              <p className="contact-msg">„{c.message}”</p>
              {c.status.value === 'WYSLANA' && (
                <div className="actions">
                  <button className="btn btn-good btn-small" disabled={busy === c.id} onClick={() => answer(c.id, 'accept')}>Akceptuj</button>
                  <button className="btn btn-small" disabled={busy === c.id} onClick={() => answer(c.id, 'reject')}>Odrzuć</button>
                </div>
              )}
              {c.fromEmail && <p className="small">Kontakt: <a href={`mailto:${c.fromEmail}`}>{c.fromEmail}</a></p>}
            </article>
          ))}
          {outgoing.length > 0 && <h3 className="sub">Wysłane przez Ciebie</h3>}
          {outgoing.map((c) => (
            <article key={c.id} className="contact">
              <div className="contact-head">
                <div>
                  do <strong><Link to={`/people/${c.to.id}`}>{c.to.name}</Link></strong>
                  <span className="muted small"> · <Link to={`/innovations/${c.innovation.id}`}>{c.innovation.name}</Link></span>
                </div>
                <Badge tone={CONTACT_TONE[c.status.value]}>{c.status.label}</Badge>
              </div>
              {c.toEmail && <p className="small">Kontakt: <a href={`mailto:${c.toEmail}`}>{c.toEmail}</a></p>}
            </article>
          ))}
        </div>
      )}
    </Section>
  )
}
