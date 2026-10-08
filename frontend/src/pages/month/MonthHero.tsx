import { formatMoney } from '../../format'
import { ArrowDown, ArrowUp } from '../../icons'
import type { MonthlySummaryResponse } from '../../types'

// Saldo do mês em destaque, com o total de receitas e de despesas.
export function MonthHero({ summary }: { summary: MonthlySummaryResponse }) {
  return (
    <section className="hero reveal">
      <div>
        <small>Saldo do mês</small>
        <p className="hero-balance">{formatMoney(summary.balance)}</p>
      </div>
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
    </section>
  )
}
