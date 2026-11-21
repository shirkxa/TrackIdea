import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from './api.js'

const AppContext = createContext(null)

export function AppProvider({ children }) {
  const [meta, setMeta] = useState(null)
  const [people, setPeople] = useState([])
  const [session, setSession] = useState({ user: null, demoMode: false, loaded: false })
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  useEffect(() => {
    Promise.all([api.get('/meta'), api.get('/people'), api.get('/auth/me')])
      .then(([m, p, me]) => {
        setMeta(m)
        setPeople(p)
        setSession({ ...me, loaded: true })
      })
      .catch(() => setError('Nie można połączyć się z backendem. Uruchom go poleceniem ./mvnw spring-boot:run w katalogu backend.'))
  }, [])

  const login = useCallback(async (email, password) => {
    const me = await api.post('/auth/login', { email, password })
    setSession({ ...me, loaded: true })
    return me.user
  }, [])

  const logout = useCallback(async () => {
    const me = await api.post('/auth/logout')
    setSession({ ...me, loaded: true })
  }, [])

  /** Tryb demo: przełączenie konta jednym kliknięciem (bez hasła) na potrzeby prezentacji. */
  const chooseUser = useCallback(async (id) => {
    const me = id ? await api.post(`/auth/demo/${id}`) : await api.post('/auth/logout')
    setSession({ ...me, loaded: true })
  }, [])

  const notify = useCallback((message, kind = 'ok') => {
    setToast({ message, kind, at: Date.now() })
    setTimeout(() => setToast((t) => (t && Date.now() - t.at >= 3500 ? null : t)), 3600)
  }, [])

  // wartość kontekstu tworzona tylko przy realnej zmianie – konsumenci nie przerysowują się bez potrzeby
  const value = useMemo(() => ({
    meta, people, user: session.user, demoMode: session.demoMode, sessionLoaded: session.loaded,
    login, logout, chooseUser, error, notify, toast,
  }), [meta, people, session, login, logout, chooseUser, error, notify, toast])
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useApp() {
  return useContext(AppContext)
}
