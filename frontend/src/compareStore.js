import { useSyncExternalStore } from 'react'

/**
 * Wybór innowacji do porównania (do 4). Osobny mały magazyn zamiast kontekstu aplikacji:
 * karta w katalogu subskrybuje tylko swój stan, więc kliknięcie „+ Porównaj” nie przerysowuje całej listy.
 */
export const COMPARE_MAX = 4
const KEY = 'trackidea.compare'
const listeners = new Set()

function read() {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(v) ? v.filter(Number.isInteger).slice(0, COMPARE_MAX) : []
  } catch {
    return []
  }
}

let ids = read()

function set(next) {
  ids = next
  try {
    localStorage.setItem(KEY, JSON.stringify(ids))
  } catch {
    // bez dostępu do pamięci przeglądarki porównanie działa do odświeżenia strony
  }
  listeners.forEach((l) => l())
}

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function toggleCompare(id) {
  if (ids.includes(id)) set(ids.filter((x) => x !== id))
  else if (ids.length < COMPARE_MAX) set([...ids, id])
}

export function clearCompare() {
  set([])
}

/** Lista wybranych id – dla paska porównania i strony porównania. */
export function useCompareIds() {
  return useSyncExternalStore(subscribe, () => ids)
}

/** Stan jednej karty: 'on' (wybrana), 'full' (limit osiągnięty) albo 'off'. Zmienia się rzadko. */
export function useCompareState(id) {
  return useSyncExternalStore(subscribe, () => (ids.includes(id) ? 'on' : ids.length >= COMPARE_MAX ? 'full' : 'off'))
}
