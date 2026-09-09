import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import IncidentListPage from './pages/IncidentListPage'
import IncidentDetailPage from './pages/IncidentDetailPage'
import ProtectedRoute from './components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/incidents"
        element={
          <ProtectedRoute>
            <IncidentListPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/incidents/:id"
        element={
          <ProtectedRoute>
            <IncidentDetailPage />
          </ProtectedRoute>
        }
      />
      <Route path="/" element={<Navigate to="/incidents" replace />} />
      <Route path="*" element={<Navigate to="/incidents" replace />} />
    </Routes>
  )
}
