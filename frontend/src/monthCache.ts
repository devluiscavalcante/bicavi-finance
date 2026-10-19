import { api, ApiError, getToken } from './api'
import type { MonthlySummaryResponse, TransactionResponse } from './types'

// Cache em memória dos meses já buscados. O backend responde em ~10 ms; o que
// demora é a ida e volta pela rede (Tailscale, 4G). Com o cache, voltar a um mês
// já visto (ou a um vizinho pré-carregado) mostra os dados na hora, e a tela busca
// de novo em segundo plano para atualizar (stale-while-revalidate).
//
// Fica no módulo, fora do React: sobrevive a ir para Categorias e voltar.
// Some ao recarregar a página (nada de dado financeiro gravado no aparelho).

export interface MonthData {
  summary: MonthlySummaryResponse
  transactions: TransactionResponse[]
}

// Resultado de uma busca, junto com o mês a que ele pertence.
export type MonthResult = { month: string } & ({ data: MonthData } | { error: string })

const data = new Map<string, MonthData>() // último resultado bom de cada mês
const pending = new Map<string, Promise<MonthResult>>() // buscas em andamento
// Muda a cada clearMonthCache(). Uma busca que começou ANTES da limpeza pode
// trazer dados de antes de uma alteração: com a geração antiga, não é guardada.
let generation = 0
// Dono do cache: se o token mudar (outro login), nada do anterior pode aparecer.
let owner: string | null = null

function checkOwner() {
  const token = getToken()
  if (token !== owner) {
    clearMonthCache()
    owner = token
  }
}

// Dados guardados do mês, se houver (podem estar desatualizados).
export function cachedMonth(month: string): MonthData | undefined {
  checkOwner()
  return data.get(month)
}

// Busca o mês na API. Se já existe uma busca dele em andamento (ex.: o
// pré-carregamento), reaproveita a mesma em vez de fazer outra. Nunca rejeita:
// um erro vira { error } e não é guardado.
export function fetchMonth(month: string): Promise<MonthResult> {
  checkOwner()
  const inFlight = pending.get(month)
  if (inFlight) return inFlight

  const startedAt = generation
  // As duas chamadas são independentes: Promise.all faz as duas ao mesmo tempo.
  const request = Promise.all([
    api<MonthlySummaryResponse>(`/api/reports/monthly-summary?month=${month}`),
    api<TransactionResponse[]>(`/api/transactions?month=${month}`),
  ])
    .then(([summary, transactions]): MonthResult => {
      if (startedAt === generation) data.set(month, { summary, transactions })
      return { month, data: { summary, transactions } }
    })
    .catch((e): MonthResult => ({
      month,
      error: e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor',
    }))
    .finally(() => {
      if (pending.get(month) === request) pending.delete(month)
    })

  pending.set(month, request)
  return request
}

// Pré-carrega um mês que ainda não está guardado nem sendo buscado.
export function prefetchMonth(month: string) {
  checkOwner()
  if (!data.has(month) && !pending.has(month)) void fetchMonth(month)
}

// Qualquer alteração (salvar, excluir, pagar) limpa TUDO: parcelas e "esta e as
// próximas" mudam outros meses, e descobrir quais seria complicado e frágil.
export function clearMonthCache() {
  data.clear()
  pending.clear()
  generation++
}
