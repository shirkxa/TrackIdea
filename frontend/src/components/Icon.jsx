/** Liniowe ikony (stroke = currentColor), spójne z kafelkami nawigacji. */
const PATHS = {
  catalog: (
    <>
      <rect x="4" y="4" width="7" height="7" rx="1.5" />
      <rect x="13" y="4" width="7" height="7" rx="1.5" />
      <rect x="4" y="13" width="7" height="7" rx="1.5" />
      <rect x="13" y="13" width="7" height="7" rx="1.5" />
    </>
  ),
  idea: (
    <>
      <path d="M9 18h6M10 21h4" />
      <path d="M12 3a6 6 0 0 0-3.6 10.8c.7.5 1.1 1.3 1.1 2.2h5c0-.9.4-1.7 1.1-2.2A6 6 0 0 0 12 3Z" />
      <path d="M12 7v4M10 9h4" />
    </>
  ),
  needs: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <path d="M8 9h8M8 13h8M8 17h5" />
    </>
  ),
  radar: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="4.5" />
      <path d="M12 12l6-6" />
      <circle cx="16" cy="14.5" r="0.8" />
    </>
  ),
  chart: (
    <>
      <path d="M4 20h16" />
      <rect x="6" y="11" width="3" height="7" rx="1" />
      <rect x="11" y="6" width="3" height="12" rx="1" />
      <rect x="16" y="13" width="3" height="5" rx="1" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4 4" />
    </>
  ),
  fork: (
    <>
      <circle cx="7" cy="5" r="2" />
      <circle cx="17" cy="5" r="2" />
      <circle cx="12" cy="19" r="2" />
      <path d="M7 7v2a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3V7M12 12v5" />
    </>
  ),
}

export default function Icon({ name, size = 24 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {PATHS[name]}
    </svg>
  )
}
