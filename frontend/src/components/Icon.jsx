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
  car: (
    <>
      <path d="M5 16V12l2-5h10l2 5v4" />
      <path d="M4 16h16v2H4z" />
      <circle cx="8" cy="13.5" r="1" />
      <circle cx="16" cy="13.5" r="1" />
    </>
  ),
  heart: <path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" />,
  chip: (
    <>
      <rect x="7" y="7" width="10" height="10" rx="2" />
      <path d="M10 4v3M14 4v3M10 17v3M14 17v3M4 10h3M4 14h3M17 10h3M17 14h3" />
    </>
  ),
  plane: <path d="M3 13l7-2 4-7 2 1-2 6 5 1 2-2h2l-2 4 2 4h-2l-2-2-5 1 2 6-2 1-4-7-7-2z" />,
  rocket: (
    <>
      <path d="M12 3c3 2 5 6 4 11l-2 3h-4l-2-3c-1-5 1-9 4-11Z" />
      <circle cx="12" cy="10" r="1.5" />
      <path d="M8 14l-3 3 3 1M16 14l3 3-3 1M11 20h2" />
    </>
  ),
  cap: (
    <>
      <path d="M3 9l9-4 9 4-9 4z" />
      <path d="M7 11v4c1.5 1.5 3 2 5 2s3.5-.5 5-2v-4M21 9v5" />
    </>
  ),
  building: (
    <>
      <path d="M4 20h16M6 20V9l6-4 6 4v11" />
      <path d="M9 20v-5h6v5M9 11h.01M12 11h.01M15 11h.01" />
    </>
  ),
  sprout: (
    <>
      <path d="M12 20v-8" />
      <path d="M12 12C12 8 9 6 5 6c0 4 3 6 7 6ZM12 14c0-4 3-6 7-6 0 4-3 6-7 6Z" />
    </>
  ),
  bolt: <path d="M13 3L5 13h6l-1 8 8-10h-6z" />,
  pulse: <path d="M3 12h4l2-5 4 10 2-5h6" />,
  tree: (
    <>
      <path d="M12 21v-5" />
      <path d="M12 3l5 7h-3l4 6H6l4-6H7z" />
    </>
  ),
  star: <path d="M12 4l2.4 5 5.6.6-4.2 3.8 1.2 5.6L12 16.2 7 19l1.2-5.6L4 9.6 9.6 9z" />,
}

export default function Icon({ name, size = 24 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {PATHS[name]}
    </svg>
  )
}
