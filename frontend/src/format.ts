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

export const PAYMENT_METHODS = Object.keys(PAYMENT_LABELS) as PaymentMethod[]

// Data de hoje no formato da API ("2026-10-07"), pelo relógio LOCAL.
export function todayIso(): string {
  const now = new Date()
  return `${toMonthString(now.getFullYear(), now.getMonth() + 1)}-${String(now.getDate()).padStart(2, '0')}`
}

// "2026-10-07" -> "2026-10"
export function monthOf(isoDate: string): string {
  return isoDate.slice(0, 7)
}

// Texto digitado -> número para a API, ou null se inválido.
// Aceita "30", "30,5", "30.50". Recusa separador de milhar ("1.234,56"),
// mais de 2 casas e mais de 10 dígitos inteiros (o mesmo @Digits do backend).
// Converter para number é seguro: até 15 dígitos significativos o double
// representa o decimal sem perda, e aqui são no máximo 12.
export function parseAmount(text: string): number | null {
  const normalized = text.trim().replace(',', '.')
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(normalized)) {
    return null
  }
  const value = Number(normalized)
  return value > 0 ? value : null
}
