import { formatMoney } from '../../format'
import { ArrowDown, ArrowUp, BarChart } from '../../icons'
import type { MonthlySummaryResponse } from '../../types'

// Saldo do mês em destaque, com o total de receitas e de despesas
// e quanto das despesas já foi pago. Tocar no saldo abre a evolução mês a mês.
export function MonthHero({ summary, onOpenTrend }: {
  summary: MonthlySummaryResponse
  onOpenTrend: () => void
}) {
  return (
    <section className="hero reveal">
      {/* span e não p: dentro de <button> só vale conteúdo "de linha". */}
      <button type="button" className="hero-balance-btn" onClick={onOpenTrend}>
        <small>Saldo do mês <BarChart /><span className="sr-only">, ver a evolução</span></small>
        <span className="hero-balance">{formatMoney(summary.balance)}</span>
      </button>
      <div className="hero-row">
        <div className="hero-stat">
          <ArrowUp />
          <div>
            <small>Receitas</small>
            <strong>{formatMoney(summary.totalIncome)}</strong>
          </div>
        </div>
        <div className="hero-stat">
          <ArrowDown />
          <div>
            <small>Despesas</small>
            <strong>{formatMoney(summary.totalExpense)}</strong>
          </div>
        </div>
      </div>
      {summary.totalExpense > 0 && (
        <div className="hero-paid">
          <div className="hero-paid-bar">
            {/* Proporção só para a largura; os valores exibidos vêm prontos da API. */}
            <span style={{ width: `${(summary.totalPaid / summary.totalExpense) * 100}%` }} />
          </div>
          <div className="hero-paid-labels">
            <small>Pago <strong>{formatMoney(summary.totalPaid)}</strong></small>
            <small>A pagar <strong>{formatMoney(summary.totalToPay)}</strong></small>
          </div>
        </div>
      )}
    </section>
  )
}
