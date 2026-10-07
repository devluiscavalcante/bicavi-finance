import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, clearToken } from '../api'
import type { UserResponse } from '../types'

// Provisória: só prova que o token funciona. No passo 3 vira a tela do mês.
export function HomePage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserResponse | null>(null)

  useEffect(() => {
    api<UserResponse>('/api/me').then(setUser).catch(() => {
      // 401 já é tratado em api(); outros erros deixam a tela em "Carregando".
    })
  }, [])

  // Com JWT stateless não há sessão no servidor: sair = esquecer o token.
  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <main>
      <h1>Bicavi</h1>
      <p>{user ? `Olá, ${user.name}!` : 'Carregando...'}</p>
      <button onClick={handleLogout}>Sair</button>
    </main>
  )
}
