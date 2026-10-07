import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api'
import { register } from '../auth'

export function RegisterPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setLoading(true)
    try {
      await register(name, email, password)
      navigate('/', { replace: true })
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Não foi possível conectar ao servidor')
      }
    } finally {
      setLoading(false)
    }
  }

  // minLength/maxLength repetem as regras do RegisterRequest só para dar
  // feedback rápido. A validação que vale é a do backend (@Valid).
  return (
    <main>
      <h1>Criar conta</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Nome
          <input value={name} onChange={e => setName(e.target.value)}
                 autoComplete="name" maxLength={100} required />
        </label>
        {fieldErrors.name && <p className="error">{fieldErrors.name}</p>}
        <label>
          E-mail
          <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                 autoComplete="email" maxLength={255} required />
        </label>
        {fieldErrors.email && <p className="error">{fieldErrors.email}</p>}
        <label>
          Senha (mínimo 8 caracteres)
          <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                 autoComplete="new-password" minLength={8} maxLength={72} required />
        </label>
        {fieldErrors.password && <p className="error">{fieldErrors.password}</p>}
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>{loading ? 'Criando...' : 'Criar conta'}</button>
      </form>
      <p>Já tem conta? <Link to="/login">Entrar</Link></p>
    </main>
  )
}
