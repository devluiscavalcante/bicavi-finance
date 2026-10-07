import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { getToken } from './api'

// Sem token, nem tenta renderizar a página: manda para o login.
// Isto é só conveniência de tela. Quem protege os dados de verdade é o backend.
export function RequireAuth({ children }: { children: ReactNode }) {
  if (!getToken()) {
    return <Navigate to="/login" replace />
  }
  return children
}
