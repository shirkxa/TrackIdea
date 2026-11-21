import SectorAvatar from './SectorAvatar.jsx'

/** Emblematy instytucji (public/avatars) – „zdjęcie profilowe” innowacji. */
const FILES = {
  '01': '01-school-book-cap.svg',
  '02': '02-school-shield-sun.svg',
  '03': '03-school-student-book.svg',
  '04': '04-book-mountains.svg',
  '05': '05-lotus-book-reader.svg',
  '06': '06-school-torch-crest.svg',
  '07': '07-caring-hands-circle.svg',
  '08': '08-helping-hand-ring.svg',
  '09': '09-community-people.svg',
  '10': '10-colorful-houses.svg',
}

/** Pule emblematów pasujące do typu instytucji; wybór stały dla danej instytucji (po jej id). */
const POOLS = {
  SZKOLA: ['01', '02', '03', '05', '06'],
  UCZELNIA: ['01', '06', '04'],
  JST: ['10', '09'],
  INSTYTUCJA_PUBLICZNA: ['07', '05', '08', '09'],
}

const ALL = Object.keys(FILES)

function logoFor(institution) {
  if (!institution) return null
  const pool = POOLS[institution.type?.value] ?? ALL
  return `/avatars/${FILES[pool[institution.id % pool.length]]}`
}

export default function InstitutionLogo({ institution, sector, size = 48 }) {
  const src = logoFor(institution)
  if (!src) return <SectorAvatar sector={sector} size={size} />
  return (
    <span className="inst-logo" style={{ width: size, height: size }} title={institution.name}>
      <img src={src} alt="" loading="lazy" />
    </span>
  )
}
