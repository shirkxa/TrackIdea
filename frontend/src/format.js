/** Data po polsku, np. „21 lis 2026”. */
export function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('pl-PL', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Polska odmiana liczebnika: 1 innowacja, 2 innowacje, 5 innowacji. */
export function plural(n, one, few, many) {
  if (n === 1) return one
  const d = n % 10
  const t = n % 100
  return d >= 2 && d <= 4 && (t < 12 || t > 14) ? few : many
}

/** Data i godzina, np. „21 lis, 17:24”. */
export function formatDateTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('pl-PL', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}
