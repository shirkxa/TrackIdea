import Icon from './Icon.jsx'

/** Kolorowy „awatar” innowacji: ikona i kolor zależne od sektora (zawsze z podpisem sektora obok). */
const SECTORS = {
  MOTORYZACJA: { icon: 'car', color: '#c2410c' },
  JAKOSC_ZYCIA: { icon: 'heart', color: '#be185d' },
  ICT: { icon: 'chip', color: '#4338ca' },
  LOTNICTWO: { icon: 'plane', color: '#0369a1' },
  KOSMOS: { icon: 'rocket', color: '#6d28d9' },
  EDUKACJA: { icon: 'cap', color: '#1d4ed8' },
  ADMINISTRACJA: { icon: 'building', color: '#0f766e' },
  ROLNICTWO: { icon: 'sprout', color: '#4d7c0f' },
  ENERGETYKA: { icon: 'bolt', color: '#a16207' },
  ZDROWIE: { icon: 'pulse', color: '#b91c1c' },
  SRODOWISKO: { icon: 'tree', color: '#15803d' },
}

const FALLBACK = { icon: 'star', color: '#0b3a9e' }

export default function SectorAvatar({ sector, size = 48 }) {
  const s = SECTORS[sector?.value] ?? FALLBACK
  return (
    <span className="sector-avatar" style={{ width: size, height: size, '--c': s.color }}
      title={sector?.label ?? 'Innowacja'}>
      <Icon name={s.icon} size={Math.round(size * 0.55)} />
    </span>
  )
}
