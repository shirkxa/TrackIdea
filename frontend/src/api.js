// Klient REST API backendu. W dev Vite proxuje /api do Spring Boot (vite.config.js),
// na produkcji można wskazać inny adres przez zmienną VITE_API_URL.
const BASE = import.meta.env.VITE_API_URL ?? ''

let currentUserId = null

export function setApiUser(id) {
  currentUserId = id
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (currentUserId) headers['X-User-Id'] = String(currentUserId)
  const res = await fetch(`${BASE}/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
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
