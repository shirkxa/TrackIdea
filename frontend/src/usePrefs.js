import { useEffect, useState } from 'react'

/** Preferencje dostępności: motyw (jasny/ciemny) i wielkość tekstu, zapamiętywane w przeglądarce. */
const KEY = 'trackidea.prefs'

function readPrefs() {
  let stored = {}
  try {
    stored = JSON.parse(localStorage.getItem(KEY) ?? '{}')
  } catch {
    stored = {}
  }
  const systemDark = typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches
  return { theme: stored.theme ?? (systemDark ? 'dark' : 'light'), fontScale: stored.fontScale ?? 0 }
}

export function usePrefs() {
  const [prefs, setPrefs] = useState(readPrefs)

  useEffect(() => {
    const root = document.documentElement
    root.dataset.theme = prefs.theme
    root.dataset.font = String(prefs.fontScale)
    try {
      localStorage.setItem(KEY, JSON.stringify(prefs))
    } catch {
      /* tryb prywatny – ustawienia działają tylko w tej sesji */
    }
  }, [prefs])

  return {
    ...prefs,
    toggleTheme: () => setPrefs((p) => ({ ...p, theme: p.theme === 'dark' ? 'light' : 'dark' })),
    setFontScale: (fontScale) => setPrefs((p) => ({ ...p, fontScale })),
  }
}
