// Klient REST API backendu. W dev Vite proxuje /api do Spring Boot (vite.config.js),
// na produkcji można wskazać inny adres przez zmienną VITE_API_URL.
// Logowanie: sesja w ciasteczku HttpOnly; zapisy wysyłają token CSRF z ciasteczka XSRF-TOKEN.
const BASE = import.meta.env.VITE_API_URL ?? ''

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const res = await fetch(`${BASE}/api${path}`, {
    method,
    headers,
    credentials: BASE ? 'include' : 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    if (res.status === 401 && !data?.message) throw new Error('Zaloguj się, żeby to zrobić.')
    if (res.status === 403 && !data?.message) throw new Error('Sesja wygasła – odśwież stronę i spróbuj ponownie.')
    throw new Error(data?.message ?? `Błąd serwera (${res.status})`)
  }
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body ?? {}),
  put: (path, body) => request('PUT', path, body),
}

export function query(params) {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, v)
  })
  const s = q.toString()
  return s ? `?${s}` : ''
}
