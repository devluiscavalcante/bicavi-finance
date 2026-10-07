import type { PaymentMethod } from './types'

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function formatMoney(value: number): string {
  return currency.format(value)
}

// "2026-10-07" -> "07/10". Montamos a data à mão de propósito: new Date("2026-10-07")
// interpreta como meia-noite em UTC, que no Brasil (UTC-3) ainda é dia 06.
export function formatDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${day}/${month}`
}

// Mês no formato da API: "2026-10". Usa a data LOCAL. toISOString() usaria UTC
// e, depois das 21h do último dia do mês, já devolveria o mês seguinte.
export function currentMonth(): string {
  const now = new Date()
  return toMonthString(now.getFullYear(), now.getMonth() + 1)
}

export function isValidMonth(value: string | null): value is string {
  return value !== null && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

// addMonths("2026-01", -1) -> "2025-12"
export function addMonths(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number)
  // new Date(ano, mês, 1) com mês fora de 0..11 "transborda" para o ano certo.
  const date = new Date(year, m - 1 + delta, 1)
  return toMonthString(date.getFullYear(), date.getMonth() + 1)
}

// "2026-10" -> "outubro de 2026"
export function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
}

const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  DINHEIRO: 'Dinheiro',
  DEBITO: 'Débito',
  CREDITO: 'Crédito',
  BOLETO: 'Boleto',
}

export function formatPaymentMethod(method: PaymentMethod): string {
  return PAYMENT_LABELS[method]
}

function toMonthString(year: number, month: number): string {
  return `${year}-${String(month).padStart(2, '0')}`
}
