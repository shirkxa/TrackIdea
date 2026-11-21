import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import { Loading } from './components/ui.jsx'
import { useApp } from './context.jsx'
import CatalogPage from './pages/CatalogPage.jsx'
import InnovationPage from './pages/InnovationPage.jsx'
import SubmitPage from './pages/SubmitPage.jsx'
import NeedsPage from './pages/NeedsPage.jsx'
import PciPage from './pages/PciPage.jsx'
import MePage from './pages/MePage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import InstitutionPage from './pages/InstitutionPage.jsx'

export default function App() {
  const { meta, error } = useApp()
  return (
    <Layout>
      {!meta && !error ? (
        <Loading />
      ) : (
        <Routes>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/submit" element={<SubmitPage />} />
          <Route path="/innovations/:id" element={<InnovationPage />} />
          <Route path="/innovations/:id/edit" element={<SubmitPage />} />
          <Route path="/needs" element={<NeedsPage />} />
          <Route path="/pci" element={<PciPage />} />
          <Route path="/me" element={<MePage />} />
          <Route path="/people/:id" element={<ProfilePage />} />
          <Route path="/institutions/:id" element={<InstitutionPage />} />
          <Route path="*" element={<p>Nie ma takiej strony.</p>} />
        </Routes>
      )}
    </Layout>
  )
}
