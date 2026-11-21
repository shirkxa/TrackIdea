/** Adres strony dla wyniku dopasowania (innowacja, partner albo potrzeba). */
export function targetLink(t) {
  if (t.kind === 'INNOWACJA') return `/innovations/${t.id}`
  return t.institutionId ? `/institutions/${t.institutionId}` : '/needs'
}
