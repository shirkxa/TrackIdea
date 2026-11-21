import Icon from './Icon.jsx'

/** Kolorowy „awatar” innowacji: ikona i kolor zależne od sektora (zawsze z podpisem sektora obok). */
const SECTORS = {
  MOTORYZACJA: { icon: 'car', color: '#c2410c', bg: '#ffedd5' },
  JAKOSC_ZYCIA: { icon: 'heart', color: '#be185d', bg: '#fce7f3' },
  ICT: { icon: 'chip', color: '#4338ca', bg: '#e0e7ff' },
  LOTNICTWO: { icon: 'plane', color: '#0369a1', bg: '#e0f2fe' },
  KOSMOS: { icon: 'rocket', color: '#6d28d9', bg: '#ede9fe' },
  EDUKACJA: { icon: 'cap', color: '#1d4ed8', bg: '#dbeafe' },
  ADMINISTRACJA: { icon: 'building', color: '#0f766e', bg: '#ccfbf1' },
  ROLNICTWO: { icon: 'sprout', color: '#4d7c0f', bg: '#ecfccb' },
  ENERGETYKA: { icon: 'bolt', color: '#a16207', bg: '#fef9c3' },
  ZDROWIE: { icon: 'pulse', color: '#b91c1c', bg: '#fee2e2' },
  SRODOWISKO: { icon: 'tree', color: '#15803d', bg: '#dcfce7' },
}

const FALLBACK = { icon: 'star', color: '#0b3a9e', bg: '#e8eefc' }

export default function SectorAvatar({ sector, size = 48 }) {
  const s = SECTORS[sector?.value] ?? FALLBACK
  return (
    <span className="sector-avatar" style={{ width: size, height: size, color: s.color, background: s.bg }}
      title={sector?.label ?? 'Innowacja'}>
      <Icon name={s.icon} size={Math.round(size * 0.55)} />
    </span>
  )
}
