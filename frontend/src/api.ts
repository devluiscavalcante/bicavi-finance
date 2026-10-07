// Cliente HTTP único do app: toda chamada à API passa por api().

const TOKEN_KEY = 'bicavi.token'

// localStorage: o login sobrevive ao F5. Contrapartida: um script injetado (XSS)
// conseguiria ler o token. O React escapa HTML por padrão, o que reduz esse risco.
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

// Erro vindo da API, já com a mensagem do ProblemDetail (RFC 9457) do backend.
export class ApiError extends Error {
  readonly status: number
  // Erros de validação por campo (@Valid), quando houver.
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...options, headers })

  // 401 COM token = token expirado ou inválido: sai e volta para o login.
  // 401 SEM token é outra coisa (ex.: senha errada no login) e vira erro normal.
  if (response.status === 401 && token) {
    clearToken()
    window.location.assign('/login')
    throw new ApiError(401, 'Sessão expirada. Entre novamente.')
  }

  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.detail ?? `Erro ${response.status}`,
      problem.errors ?? {},
    )
  }

  // 204 No Content (ex.: DELETE) não tem corpo.
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}
