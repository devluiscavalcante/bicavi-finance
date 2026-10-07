// Espelham os records do backend.
//
// Dinheiro chega como number (BigDecimal serializado pelo Jackson). O frontend
// só EXIBE esses valores: nenhuma conta aqui, os totais vêm prontos da API.

export interface UserResponse {
  id: number
  email: string
  name: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type TransactionType = 'INCOME' | 'EXPENSE'

export type PaymentMethod = 'PIX' | 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'BOLETO'

export interface TransactionResponse {
  id: number
  amount: number
  type: TransactionType
  paymentMethod: PaymentMethod | null // null em receitas
  categoryId: number | null
  categoryName: string | null
  description: string | null
  occurredOn: string // "2026-10-07"
}

export interface CategoryTotal {
  categoryId: number | null // null = "Sem categoria"
  categoryName: string | null
  total: number
}

export interface MonthlySummaryResponse {
  month: string // "2026-10"
  totalIncome: number
  totalExpense: number
  balance: number
  expensesByCategory: CategoryTotal[]
}

// Limites de período calculados pelo backend (GET /api/period).
// Meses no formato "2026-10": comparar como texto funciona ("2026-09" < "2026-10").
export interface PeriodResponse {
  firstEditableMonth: string // deste mês em diante: criar, editar e excluir
  oldestVisibleMonth: string // antes disso a API não devolve dados
}

export interface CategoryResponse {
  id: number
  name: string
  type: TransactionType
}

// Corpo do POST /api/transactions.
export interface TransactionRequest {
  amount: number
  type: TransactionType
  paymentMethod: PaymentMethod | null
  categoryId: number | null
  description: string | null
  occurredOn: string
}
