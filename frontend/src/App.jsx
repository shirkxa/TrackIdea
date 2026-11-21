import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import { Loading } from './components/ui.jsx'
import { useApp } from './context.jsx'
import CatalogPage from './pages/CatalogPage.jsx'
import InnovationPage from './pages/InnovationPage.jsx'
import EditPage from './pages/EditPage.jsx'
import NeedsPage from './pages/NeedsPage.jsx'
import PciPage from './pages/PciPage.jsx'
import InstitutionPage from './pages/InstitutionPage.jsx'
import LoginPage from './pages/LoginPage.jsx'

export default function App() {
  const { meta, error } = useApp()
  return (
    <Layout>
      {!meta && !error ? (
        <Loading />
      ) : (
        <Routes>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/innovations/:id" element={<InnovationPage />} />
          <Route path="/innovations/:id/edit" element={<EditPage />} />
          <Route path="/needs" element={<NeedsPage />} />
          <Route path="/pci" element={<PciPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/institutions/:id" element={<InstitutionPage />} />
          <Route path="*" element={<p>Nie ma takiej strony.</p>} />
        </Routes>
      )}
    </Layout>
  )
}
