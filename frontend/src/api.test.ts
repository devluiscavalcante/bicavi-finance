import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getToken, saveToken } from './api'

// Os testes rodam no Node, sem navegador: localStorage, fetch e window
// são trocados por versões falsas, controladas pelo teste.
function fakeLocalStorage(): Storage {
  const data = new Map<string, string>()
  return {
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: key => void data.delete(key),
    clear: () => data.clear(),
    key: index => [...data.keys()][index] ?? null,
    get length() {
      return data.size
    },
  }
}

function problem(status: number, detail: string) {
  return new Response(JSON.stringify({ status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()
const assignMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('localStorage', fakeLocalStorage())
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('window', { location: { assign: assignMock } })
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
  assignMock.mockReset()
})

function sentHeaders(): Headers {
  return fetchMock.mock.calls[0][1]!.headers as Headers
}

describe('api', () => {
  it('envia o token nas rotas protegidas', async () => {
    saveToken('abc')
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))

    await api('/api/me')

    expect(sentHeaders().get('Authorization')).toBe('Bearer abc')
  })

  it('não envia token no login (um token vencido faria o backend responder 401)', async () => {
    saveToken('vencido')
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))

    await api('/api/auth/login', { method: 'POST', body: '{}' })

    expect(sentHeaders().has('Authorization')).toBe(false)
  })

  it('senha errada com token guardado mostra o erro e não trata como sessão expirada', async () => {
    saveToken('abc')
    fetchMock.mockResolvedValue(problem(401, 'E-mail ou senha incorretos'))

    const request = api('/api/auth/login', { method: 'POST', body: '{}' })

    await expect(request).rejects.toBeInstanceOf(ApiError)
    await expect(request).rejects.toThrow('E-mail ou senha incorretos')
    expect(assignMock).not.toHaveBeenCalled()
  })

  it('401 numa rota protegida apaga o token e volta para o login', async () => {
    saveToken('vencido')
    fetchMock.mockResolvedValue(problem(401, 'Autenticação necessária'))

    await expect(api('/api/me')).rejects.toThrow('Sessão expirada')

    expect(getToken()).toBeNull()
    expect(assignMock).toHaveBeenCalledWith('/login')
  })
})
