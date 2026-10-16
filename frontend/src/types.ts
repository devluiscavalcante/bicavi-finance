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
  cardId: number | null // só no crédito (despesas antigas no crédito podem estar sem)
  cardName: string | null
  categoryId: number | null
  categoryName: string | null
  description: string | null
  occurredOn: string // "2026-10-07"
  paidOn: string | null // data do pagamento; null = a pagar (receitas: sempre null)
  installmentNumber: number | null // parcela 2 de 3 -> 2; null se não for parcela
  installmentCount: number | null // parcela 2 de 3 -> 3
}

// Alcance de edição/exclusão de uma parcela (?scope=...).
export type EditScope = 'THIS' | 'FOLLOWING'

export interface CategoryTotal {
  categoryId: number | null // null = "Sem categoria"
  categoryName: string | null
  total: number
}

// Fatura do cartão no mês: as compras no crédito com aquele cartão. São as
// MESMAS despesas de expensesByCategory, agrupadas de outro jeito (não somar).
export interface CardTotal {
  cardId: number | null // null = compras no crédito antigas, sem cartão
  cardName: string
  total: number
  toPay: number // compras ainda não pagas; 0 = fatura paga
  purchases: number
}

export interface MonthlySummaryResponse {
  month: string // "2026-10"
  totalIncome: number
  totalExpense: number
  totalPaid: number // despesas já pagas
  totalToPay: number // despesas a pagar (pago + a pagar = totalExpense)
  balance: number
  expensesByCategory: CategoryTotal[]
  expensesByCard: CardTotal[] // da maior fatura para a menor
}

// Limites de período calculados pelo backend (GET /api/period).
// Meses no formato "2026-10": comparar como texto funciona ("2026-09" < "2026-10").
export interface PeriodResponse {
  firstEditableMonth: string // deste mês em diante: criar, editar e excluir
  oldestVisibleMonth: string // antes disso a API não devolve dados
  lastMonth: string // horizonte de planejamento: depois disso não dá para lançar nem consultar
}

export interface CategoryResponse {
  id: number
  name: string
  type: TransactionType
}

// Cartão de crédito: separado das categorias (categoria = o que comprei,
// cartão = como paguei).
export interface CardResponse {
  id: number
  name: string
}

// Corpo do POST /api/transactions/installments (compra parcelada, sempre despesa).
export interface InstallmentRequest {
  totalAmount: number // total da compra; o backend divide
  installments: number // 2 a 24
  paymentMethod: PaymentMethod | null
  cardId: number | null // obrigatório no crédito; o mesmo em todas as parcelas
  categoryId: number | null
  description: string | null
  firstDate: string // data da 1ª parcela; as demais nos meses seguintes
}

// Corpo do PATCH .../payment (despesa ou fatura inteira).
export interface PaymentRequest {
  paid: boolean
}

// Corpo do POST /api/transactions.
export interface TransactionRequest {
  amount: number
  type: TransactionType
  paymentMethod: PaymentMethod | null
  cardId: number | null // obrigatório no crédito, proibido nas demais formas
  categoryId: number | null
  description: string | null
  occurredOn: string
}
