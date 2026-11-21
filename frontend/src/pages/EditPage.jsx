import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api.js'
import { ErrorBox, Loading, Section, SelectEnum } from '../components/ui.jsx'
import { useApp } from '../context.jsx'
import { useFetch } from '../useFetch.js'

/** Poprawa karty przez analityka PCI – np. gdy import ze źródła rozpoznał coś błędnie. */
function toForm(d) {
  return {
    title: d.card.title ?? '', summary: d.card.summary ?? '', problem: d.problem ?? '', solution: d.solution ?? '',
    requirements: d.requirements ?? '', results: d.results ?? '', type: d.card.type?.value ?? null,
    sector: d.card.sector?.value ?? null, maturity: d.card.maturity?.value ?? null,
    transferModel: d.card.transferModel?.value ?? null, ipStatus: d.ipStatus ?? '',
    tags: [...(d.card.tags ?? [])].join(', '), links: [...(d.links ?? [])].join('\n'),
    institutionId: d.card.institution?.id ?? null, sourceAuthors: d.card.authors ?? '',
    trl: d.card.trl?.level ?? null,
  }
}

export default function EditPage() {
  const { id } = useParams()
  const { user } = useApp()
  const existing = useFetch(`/innovations/${id}`, [user?.id])

  if (existing.error) return <ErrorBox>{existing.error}</ErrorBox>
  if (!existing.data || String(existing.data.card.id) !== id) return <Loading />
  if (!existing.data.viewer.canEdit) {
    return <ErrorBox>Kartę może poprawić tylko analityk PCI. <Link to="/login">Zaloguj się</Link>.</ErrorBox>
  }
  return <EditForm key={id} id={id} initial={toForm(existing.data)} />
}

function EditForm({ id, initial }) {
  const { meta, notify } = useApp()
  const trlOptions = (meta.trlLevels ?? []).map((t) => ({ value: t.level, label: `TRL ${t.level} – ${t.label}` }))
  const navigate = useNavigate()
  const institutions = useFetch('/institutions')
  const [form, setForm] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const set = (key) => (value) => setForm((f) => ({ ...f, [key]: value }))
  const input = (key) => ({ value: form[key] ?? '', onChange: (e) => set(key)(e.target.value) })

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const res = await api.put(`/innovations/${id}`, {
        ...form,
        tags: form.tags.split(',').map((t) => t.trim()).filter(Boolean),
        links: form.links.split('\n').map((t) => t.trim()).filter(Boolean),
      })
      notify('Zapisano zmiany.')
      navigate(`/innovations/${res.card.id}`)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="submit-page">
      <h1 className="page-title">Popraw kartę</h1>
      <ErrorBox>{error}</ErrorBox>
      <Section title="Karta innowacji">
        <div className="form">
          <label className="field wide"><span>Tytuł *</span><input {...input('title')} maxLength={200} /></label>
          <label className="field wide"><span>Krótki opis</span><textarea rows={2} maxLength={500} {...input('summary')} /></label>
          <label className="field wide"><span>Problem</span><textarea rows={3} {...input('problem')} /></label>
          <label className="field wide"><span>Rozwiązanie</span><textarea rows={4} {...input('solution')} /></label>
          <label className="field wide"><span>Wymagania wdrożenia</span><textarea rows={2} {...input('requirements')} /></label>
          <label className="field wide"><span>Efekty</span><textarea rows={2} {...input('results')} /></label>
          <SelectEnum label="Typ" value={form.type} onChange={set('type')} options={meta.types} placeholder="Wybierz…" />
          <SelectEnum label="Sektor" value={form.sector} onChange={set('sector')} options={meta.sectors} placeholder="Wybierz…" />
          <SelectEnum label="Etap wdrożenia" value={form.maturity} onChange={set('maturity')} options={meta.maturities} placeholder="Wybierz…" />
          <label className="field">
            <span>Gotowość technologiczna (TRL)</span>
            <select value={form.trl ?? ''} onChange={(e) => set('trl')(e.target.value ? Number(e.target.value) : null)}>
              <option value="">Wybierz…</option>
              {trlOptions.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </label>
          <SelectEnum label="Model transferu" value={form.transferModel} onChange={set('transferModel')} options={meta.transferModels} placeholder="Wybierz…" />
          <label className="field">
            <span>Instytucja</span>
            <select value={form.institutionId ?? ''} onChange={(e) => set('institutionId')(e.target.value ? Number(e.target.value) : null)}>
              <option value="">Wybierz…</option>
              {institutions.data?.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
          </label>
          <label className="field"><span>Autorzy (według źródła)</span><input {...input('sourceAuthors')} /></label>
          <label className="field"><span>Ochrona IP</span><input {...input('ipStatus')} /></label>
          <label className="field wide"><span>Tagi (po przecinku)</span><input {...input('tags')} /></label>
          <label className="field wide"><span>Linki do źródeł (każdy w osobnej linii)</span><textarea rows={2} {...input('links')} /></label>
        </div>
        <div className="actions">
          <button className="btn btn-primary" disabled={busy || !form.title.trim()} onClick={save}>Zapisz zmiany</button>
          <Link className="btn" to={`/innovations/${id}`}>Anuluj</Link>
        </div>
      </Section>
    </div>
  )
}
