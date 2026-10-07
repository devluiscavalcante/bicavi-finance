import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api'
import { login } from '../auth'
import { AuthLayout } from '../AuthLayout'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    // Sem isto o navegador recarregaria a página ao enviar o formulário.
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout
      title="Bem-vindo de volta"
      subtitle="Entre para ver suas finanças do mês."
      footer={<>Não tem conta? <Link to="/cadastro">Cadastre-se</Link></>}
    >
      <form onSubmit={handleSubmit}>
        <label>
          E-mail
          <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                 autoComplete="email" required />
        </label>
        <label>
          Senha
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                 autoComplete="current-password" required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </AuthLayout>
  )
}
