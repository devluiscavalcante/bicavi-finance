import { api, saveToken } from './api'
import type { LoginResponse, UserResponse } from './types'

export async function login(email: string, password: string) {
  const response = await api<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  saveToken(response.accessToken)
}

// O cadastro não devolve token, então entramos logo em seguida com os mesmos dados.
export async function register(name: string, email: string, password: string) {
  await api<UserResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  })
  await login(email, password)
}
