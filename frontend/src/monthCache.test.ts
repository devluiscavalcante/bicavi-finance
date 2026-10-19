import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getToken } from './api'
import { cachedMonth, clearMonthCache, fetchMonth, prefetchMonth } from './monthCache'

// A API de verdade faria fetch; aqui ela é trocada por funções controladas pelo teste.
// ApiError continua a classe real (o cache usa "instanceof").
vi.mock('./api', async importOriginal => ({
  ...(await importOriginal<typeof import('./api')>()),
  api: vi.fn(),
  getToken: vi.fn(() => 'token-a'),
}))

const apiMock = vi.mocked(api)
const getTokenMock = vi.mocked(getToken)

// Responde cada chamada pela URL: resumo ou lista de transações do mês.
function respondWith(label: string) {
  apiMock.mockImplementation(async (path: string) =>
    path.includes('monthly-summary') ? { label } : [{ label }])
}

beforeEach(() => {
  apiMock.mockReset()
  getTokenMock.mockReturnValue('token-a')
  clearMonthCache()
})

describe('monthCache', () => {
  it('guarda o mês buscado e devolve do cache depois', async () => {
    respondWith('outubro')
    expect(cachedMonth('2026-10')).toBeUndefined()

    const result = await fetchMonth('2026-10')

    expect(result).toEqual({ month: '2026-10', data: { summary: { label: 'outubro' }, transactions: [{ label: 'outubro' }] } })
    expect(cachedMonth('2026-10')).toEqual({ summary: { label: 'outubro' }, transactions: [{ label: 'outubro' }] })
  })

  it('reaproveita a busca em andamento em vez de chamar a API de novo', async () => {
    respondWith('outubro')

    prefetchMonth('2026-10')
    await fetchMonth('2026-10')

    // 2 chamadas (resumo + transações), não 4.
    expect(apiMock).toHaveBeenCalledTimes(2)
  })

  it('prefetch não busca mês que já está no cache', async () => {
    respondWith('outubro')
    await fetchMonth('2026-10')

    prefetchMonth('2026-10')

    expect(apiMock).toHaveBeenCalledTimes(2)
  })

  it('erro vira { error } e não é guardado', async () => {
    apiMock.mockRejectedValue(new ApiError(400, 'Mês fora do período'))

    const result = await fetchMonth('2099-01')

    expect(result).toEqual({ month: '2099-01', error: 'Mês fora do período' })
    expect(cachedMonth('2099-01')).toBeUndefined()
  })

  it('resposta que chega depois de uma limpeza não volta para o cache', async () => {
    respondWith('antes da alteração')
    const request = fetchMonth('2026-10')

    clearMonthCache() // ex.: o usuário salvou uma transação enquanto a busca viajava
    await request

    expect(cachedMonth('2026-10')).toBeUndefined()
  })

  it('outro login não vê os meses do usuário anterior', async () => {
    respondWith('do usuário A')
    await fetchMonth('2026-10')

    getTokenMock.mockReturnValue('token-b')

    expect(cachedMonth('2026-10')).toBeUndefined()
  })
})
