import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { Badge, ErrorBox, Loading, Section, SelectEnum } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { useFetch } from '../useFetch.js'

/** Przykładowe opisy do scenariuszy demo z planu projektu. */
const EXAMPLES = [
  {
    label: 'Scenariusz A – mentoring w liceum',
    text: `Program mentoringu rówieśniczego dla klas pierwszych
W naszym liceum uczniowie klas pierwszych często mają problem z adaptacją: nie znają nikogo, gubią się w nowych zasadach i rośnie liczba nieobecności. Stworzyłam program, w którym uczniowie klas drugich i trzecich zostają mentorami pierwszoklasistów. Każdy mentor opiekuje się grupą 3-4 osób przez pierwszy semestr: oprowadza po szkole, pomaga w organizacji nauki i spotyka się z grupą raz w tygodniu. Mentorzy przechodzą krótkie szkolenie z pedagogiem. Program działa od 2 lat. Liczba nieobecności w klasach pierwszych spadła o 30%, a w ankiecie 85% uczniów oceniło program dobrze. Wymaga tylko koordynatora i gotowych scenariuszy spotkań, które udostępniamy bezpłatnie.`,
  },
  {
    label: 'Scenariusz C – usterki w gminie',
    text: `Aplikacja do zgłaszania usterek przez mieszkańców
Mieszkańcy zgłaszają usterki w przestrzeni publicznej, takie jak dziury w drogach, zepsute lampy i dzikie wysypiska, telefonicznie do różnych pracowników urzędu, więc część zgłoszeń ginie. Chcemy stworzyć stronę z mapą gminy, na której mieszkaniec zaznaczy usterkę i doda zdjęcie, a urząd będzie informował o statusie naprawy.`,
  },
]

const EMPTY_FORM = {
  title: '', summary: '', problem: '', solution: '', requirements: '', results: '',
  type: null, sector: null, maturity: null, transferModel: null, ipStatus: '',
  tags: '', links: '', institutionId: null, onBehalf: false, authorInfo: '',
}

function toForm(d) {
  return {
    title: d.card.title ?? '', summary: d.card.summary ?? '', problem: d.problem ?? '', solution: d.solution ?? '',
    requirements: d.requirements ?? '', results: d.results ?? '', type: d.card.type?.value ?? null,
    sector: d.card.sector?.value ?? null, maturity: d.card.maturity?.value ?? null,
    transferModel: d.card.transferModel?.value ?? null, ipStatus: d.ipStatus ?? '',
    tags: [...(d.card.tags ?? [])].join(', '), links: [...(d.links ?? [])].join('\n'),
    institutionId: d.card.institution?.id ?? null, onBehalf: false, authorInfo: '',
  }
}

export default function SubmitPage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const { user } = useApp()
  const existing = useFetch(editing ? `/innovations/${id}` : null, [user?.id])

  if (!user) {
    return (
      <Section title={editing ? 'Edycja karty' : 'Zgłoś innowację'}>
        <p className="notice">Wybierz użytkownika demo w prawym górnym rogu (np. nauczycielkę albo opiekuna instytucji), żeby zgłosić innowację.</p>
      </Section>
    )
  }
  if (!editing) {
    return <SubmitForm key={`new-${user.id}`} initial={{ ...EMPTY_FORM, institutionId: user.institution?.id ?? null }} />
  }
  if (existing.error) return <ErrorBox>{existing.error}</ErrorBox>
  if (!existing.data || String(existing.data.card.id) !== id) return <Loading />
  if (!existing.data.viewer.canEdit) {
    return <ErrorBox>Kartę może edytować autor, opiekun instytucji lub ekspert PCI.</ErrorBox>
  }
  return <SubmitForm key={`edit-${id}`} id={id} initial={toForm(existing.data)} />
}

function SubmitForm({ id, initial }) {
  const editing = Boolean(id)
  const { meta, notify } = useApp()
  const navigate = useNavigate()
  const institutions = useFetch('/institutions')

  const [step, setStep] = useState(editing ? 2 : 1)
  const [description, setDescription] = useState('')
  const [form, setForm] = useState(initial)
  const [draftInfo, setDraftInfo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [similar, setSimilar] = useState(null)

  // Podobne innowacje pokazywane już w trakcie pisania (ograniczanie dublowania prac).
  const similarText = step === 1 ? description : `${form.title} ${form.summary} ${form.problem} ${form.solution}`
  const longEnough = similarText.trim().length >= 40
  useEffect(() => {
    if (!longEnough) return
    const t = setTimeout(() => {
      api.post('/similar', { text: similarText, excludeId: editing ? Number(id) : null })
        .then(setSimilar)
        .catch(() => setSimilar(null))
    }, 500)
    return () => clearTimeout(t)
  }, [similarText, longEnough, editing, id])

  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }))
  const input = (key) => ({ value: form[key] ?? '', onChange: (e) => set(key)(e.target.value) })

  async function runAssistant() {
    setBusy(true)
    setError(null)
    try {
      const d = await api.post('/assistant/draft', { text: description })
      setForm((f) => ({
        ...f,
        title: d.title ?? '', summary: d.summary ?? '', problem: d.problem ?? '', solution: d.solution ?? '',
        requirements: d.requirements ?? '', results: d.results ?? '', type: d.type ?? null, sector: d.sector ?? null,
        maturity: d.maturity ?? null, tags: (d.tags ?? []).join(', '),
      }))
      setDraftInfo({ engine: d.engine, notes: d.notes })
      setStep(2)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function save() {
    setBusy(true)
    setError(null)
    const body = {
      ...form,
      tags: form.tags.split(',').map((t) => t.trim()).filter(Boolean),
      links: form.links.split('\n').map((t) => t.trim()).filter(Boolean),
    }
    try {
      const res = editing ? await api.put(`/innovations/${id}`, body) : await api.post('/innovations', body)
      notify(editing ? 'Zapisano zmiany.' : form.onBehalf
        ? 'Zgłoszono – autor dostanie prośbę o potwierdzenie.'
        : 'Zgłoszono innowację.')
      navigate(`/innovations/${res.card.id}`)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="submit-page">
      <h1>{editing ? 'Edycja karty innowacji' : 'Zgłoś innowację'}</h1>
      {!editing && (
        <ol className="wizard">
          <li className={step === 1 ? 'on' : 'done'}>1. Opisz własnymi słowami</li>
          <li className={step === 2 ? 'on' : ''}>2. Sprawdź i uzupełnij kartę</li>
        </ol>
      )}
      <ErrorBox>{error}</ErrorBox>

      <div className="two-col">
        <div className="col-main">
          {step === 1 ? (
            <Section title="Opisz pomysł" subtitle="Wystarczy kilka zdań: jaki problem rozwiązuje, na czym polega, jakie są efekty.">
              <textarea rows={10} value={description} onChange={(e) => setDescription(e.target.value)}
                placeholder="Np. W naszej szkole uczniowie pierwszych klas mieli problem z… Wprowadziliśmy… Efekt: …" />
              <div className="examples">
                <span className="muted small">Przykłady do demo:</span>
                {EXAMPLES.map((ex) => (
                  <button key={ex.label} className="btn btn-small" onClick={() => setDescription(ex.text)}>{ex.label}</button>
                ))}
              </div>
              <div className="actions">
                <button className="btn btn-primary" disabled={busy || description.trim().length < 20} onClick={runAssistant}>
                  {busy ? 'Asystent analizuje opis…' : '✦ Wypełnij kartę asystentem'}
                </button>
                <button className="btn" onClick={() => setStep(2)}>Wypełnię ręcznie</button>
              </div>
              <p className="muted small">
                Asystent: {meta.aiEnabled ? 'model Claude' : 'reguły offline (ustaw ANTHROPIC_API_KEY w backendzie, by użyć Claude)'}.
                Tylko proponuje – każde pole możesz poprawić.
              </p>
            </Section>
          ) : (
            <Section title="Karta innowacji" subtitle="Pola oznaczone * są potrzebne do oceny.">
              {draftInfo && (
                <div className="notice small">
                  <Badge tone={draftInfo.engine === 'CLAUDE' ? 'accent' : 'muted'}>{draftInfo.engine === 'CLAUDE' ? 'Claude' : 'Reguły offline'}</Badge>{' '}
                  {draftInfo.notes.join(' ')}
                </div>
              )}
              <div className="form">
                <label className="field wide"><span>Tytuł *</span><input {...input('title')} maxLength={200} /></label>
                <label className="field wide"><span>Krótki opis * (do 500 znaków)</span><textarea rows={2} maxLength={500} {...input('summary')} /></label>
                <label className="field wide"><span>Problem * – czego dotyczy i kogo</span><textarea rows={3} {...input('problem')} /></label>
                <label className="field wide"><span>Rozwiązanie * – na czym polega, co nowego</span><textarea rows={4} {...input('solution')} /></label>
                <label className="field wide"><span>Wymagania wdrożenia – ludzie, sprzęt, koszt</span><textarea rows={2} {...input('requirements')} /></label>
                <label className="field wide"><span>Efekty – najlepiej liczbowe</span><textarea rows={2} {...input('results')} /></label>
                <SelectEnum label="Typ *" value={form.type} onChange={set('type')} options={meta.types} placeholder="Wybierz…" />
                <SelectEnum label="Sektor *" value={form.sector} onChange={set('sector')} options={meta.sectors} placeholder="Wybierz…" />
                <SelectEnum label="Etap dojrzałości *" value={form.maturity} onChange={set('maturity')} options={meta.maturities} placeholder="Wybierz…" />
                <SelectEnum label="Model transferu *" value={form.transferModel} onChange={set('transferModel')} options={meta.transferModels} placeholder="Wybierz…" />
                <label className="field">
                  <span>Instytucja *</span>
                  <select value={form.institutionId ?? ''} onChange={(e) => set('institutionId')(e.target.value ? Number(e.target.value) : null)}>
                    <option value="">Wybierz…</option>
                    {institutions.data?.filter((i) => i.publicSector).map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
                  </select>
                </label>
                <label className="field"><span>Ochrona IP</span><input {...input('ipStatus')} placeholder="np. open source, zgłoszenie patentowe" /></label>
                <label className="field wide"><span>Tagi (po przecinku)</span><input {...input('tags')} /></label>
                <label className="field wide"><span>Linki do materiałów (każdy w osobnej linii)</span><textarea rows={2} {...input('links')} placeholder="https://github.com/… lub film na YouTube" /></label>
                {!editing && (
                  <div className="field wide onbehalf">
                    <label className="checkbox">
                      <input type="checkbox" checked={form.onBehalf} onChange={(e) => set('onBehalf')(e.target.checked)} />
                      <span>Zgłaszam cudzy pomysł (jako zwiadowca)</span>
                    </label>
                    {form.onBehalf && (
                      <>
                        <input {...input('authorInfo')} placeholder="Kto jest autorem? Np. Anna Kowalczyk, nauczycielka biologii" />
                        <span className="muted small">Zgłoszenie będzie niepubliczne, dopóki autor go nie potwierdzi (RODO).</span>
                      </>
                    )}
                  </div>
                )}
              </div>
              <div className="actions">
                <button className="btn btn-primary" disabled={busy || !form.title.trim()} onClick={save}>
                  {editing ? 'Zapisz zmiany' : 'Zgłoś innowację'}
                </button>
                {!editing && <button className="btn" onClick={() => setStep(1)}>← Wróć do opisu</button>}
                {editing && <Link className="btn" to={`/innovations/${id}`}>Anuluj</Link>}
              </div>
            </Section>
          )}
        </div>
        <aside className="col-side">
          <SimilarPanel similar={longEnough ? similar : null} />
          <Section title="Co dalej po zgłoszeniu?">
            <ol className="steps small">
              <li>Autor potwierdza zgłoszenie (jeśli zgłosił je ktoś inny).</li>
              <li>Gdy karta jest kompletna, autor prosi o ocenę.</li>
              <li>System proponuje ocenę według 6 jawnych kryteriów, ekspert PCI ją zatwierdza.</li>
              <li>Ekspert wskazuje ścieżkę: replikacja, rozwój w PCI, grant, B+R lub komercjalizacja.</li>
              <li>Platforma poleca instytucje, partnerów i inwestorów. Wdrożenie w innym miejscu to fork.</li>
            </ol>
          </Section>
        </aside>
      </div>
    </div>
  )
}

function SimilarPanel({ similar }) {
  const top = similar?.innovations?.[0]
  return (
    <Section title="Podobne rozwiązania" subtitle="Sprawdzamy na bieżąco, czy ktoś już tego nie zrobił">
      {!similar ? (
        <p className="muted small">Zacznij pisać – pokażemy podobne innowacje i patenty.</p>
      ) : (
        <>
          {top && top.similarityPercent >= 60 && (
            <div className="notice small">
              <strong>Podobne rozwiązanie już istnieje</strong> ({top.subtitle}, {top.similarityPercent}%).
              Zamiast budować od zera, możesz je <Link to={`/innovations/${top.id}`}>sforkować albo napisać do autora</Link>.
            </div>
          )}
          {similar.innovations.length === 0 && similar.priorArt.length === 0 && <p className="muted small">Brak podobnych – wygląda na nowy pomysł.</p>}
          {similar.innovations.map((s) => (
            <div key={`i${s.id}`} className="similar">
              <Link to={`/innovations/${s.id}`} target="_blank">{s.title}</Link>{' '}
              <Badge tone={s.similarityPercent >= 60 ? 'warn' : 'muted'}>{s.similarityPercent}%</Badge>
              <div className="muted small">{s.subtitle}</div>
            </div>
          ))}
          {similar.priorArt.map((s) => (
            <div key={`p${s.id}`} className="similar">
              <Badge tone="radar">{s.kind === 'PATENT' ? 'Patent' : 'Publikacja'}</Badge> {s.title}{' '}
              <Badge tone="muted">{s.similarityPercent}%</Badge>
            </div>
          ))}
        </>
      )}
    </Section>
  )
}
