import { useCallback, useEffect, useState } from 'react'
import { api } from './api.js'

/** Pobiera dane z API i odświeża je po zmianie zależności (np. użytkownika demo). */
export function useFetch(path, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!path) return
    let alive = true
    setLoading(true)
    api
      .get(path)
      .then((d) => {
        if (alive) {
          setData(d)
          setError(null)
        }
      })
      .catch((e) => alive && setError(e.message))
      .finally(() => alive && setLoading(false))
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, tick, ...deps])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { data, setData, loading, error, reload }
}
