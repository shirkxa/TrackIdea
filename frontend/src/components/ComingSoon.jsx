import { Link } from 'react-router-dom'

/** Tymczasowy widok dla ekranów, które powstaną w kolejnych krokach. */
export default function ComingSoon({ title }) {
  return (
    <section className="section">
      <h1>{title}</h1>
      <p className="muted">Ten ekran jest w budowie.</p>
      <Link to="/">← Wróć do katalogu</Link>
    </section>
  )
}
