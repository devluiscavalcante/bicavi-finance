import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthBackground } from './AuthLayout'
import { RequireAuth } from './RequireAuth'
import { MonthPage } from './pages/MonthPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

function App() {
  return (
    <BrowserRouter>
      {/* Fundo decorativo comum a todas as telas (só CSS, ver .backdrop). */}
      <div className="backdrop" aria-hidden="true">
        <span className="glow glow-1" />
        <span className="glow glow-2" />
      </div>
      <Routes>
        {/* Rota de layout (sem path): só envolve as filhas com o fundo animado. */}
        <Route element={<AuthBackground />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/cadastro" element={<RegisterPage />} />
        </Route>
        <Route path="/" element={<RequireAuth><MonthPage /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
