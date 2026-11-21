import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setApiUser } from './api.js'

const AppContext = createContext(null)
const STORAGE_KEY = 'trackidea.userId'

function readStoredUser() {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v ? Number(v) : null
  } catch {
    return null
  }
}

export function AppProvider({ children }) {
  const [meta, setMeta] = useState(null)
  const [people, setPeople] = useState([])
  const [userId, setUserId] = useState(readStoredUser)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  setApiUser(userId)

  useEffect(() => {
    Promise.all([api.get('/meta'), api.get('/people')])
      .then(([m, p]) => {
        setMeta(m)
        setPeople(p)
      })
      .catch(() => setError('Nie można połączyć się z backendem. Uruchom go poleceniem ./mvnw spring-boot:run w katalogu backend.'))
  }, [])

  const chooseUser = useCallback((id) => {
    setUserId(id)
    setApiUser(id)
    try {
      if (id) localStorage.setItem(STORAGE_KEY, String(id))
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* tryb prywatny – pamiętamy tylko w tej sesji */
    }
  }, [])

  const notify = useCallback((message, kind = 'ok') => {
    setToast({ message, kind, at: Date.now() })
    setTimeout(() => setToast((t) => (t && Date.now() - t.at >= 3500 ? null : t)), 3600)
  }, [])

  const user = useMemo(() => people.find((p) => p.id === userId) ?? null, [people, userId])

  const value = { meta, people, user, chooseUser, error, notify, toast }
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useApp() {
  return useContext(AppContext)
}
