import { addMonths } from './format'
import type { PeriodResponse } from './types'

// Quantos meses o gráfico de evolução mostra antes e depois do mês da tela.
export const TREND_SIDE_MONTHS = 6

// Intervalo pedido à API: 6 meses antes e 6 depois, cortado nos limites de
// consulta (o backend recusaria um mês fora deles).
// Meses "AAAA-MM" comparam certo como texto.
export function trendRange(month: string, period: PeriodResponse): { from: string; to: string } {
  const from = addMonths(month, -TREND_SIDE_MONTHS)
  const to = addMonths(month, TREND_SIDE_MONTHS)
  return {
    from: from < period.oldestVisibleMonth ? period.oldestVisibleMonth : from,
    to: to > period.lastMonth ? period.lastMonth : to,
  }
}

// Escala das barras: quanto da altura fica acima e abaixo da linha do zero.
// São os maiores saldos de cada lado, usados como proporção (flex-grow): com
// sobra de 3000 e falta de 1000, 3/4 da altura ficam acima da linha.
// Só desenho: nenhum valor exibido sai daqui.
export interface TrendScale {
  above: number
  below: number
}

export function trendScale(balances: number[]): TrendScale {
  const above = Math.max(0, ...balances)
  const below = Math.max(0, ...balances.map(b => -b))
  // Tudo zerado: só a linha do zero, embaixo.
  return above === 0 && below === 0 ? { above: 1, below: 0 } : { above, below }
}

// Altura da barra em % do lado dela (acima ou abaixo do zero).
export function barPercent(balance: number, scale: TrendScale): number {
  const side = balance >= 0 ? scale.above : scale.below
  return side === 0 ? 0 : (Math.abs(balance) / side) * 100
}
