import { useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../../api'
import { formatMoney, formatMonth, formatMonthShort } from '../../format'
import { Close } from '../../icons'
import { barPercent, trendRange, trendScale } from '../../trend'
import type { MonthTrend, PeriodResponse } from '../../types'

// Painel com o saldo mês a mês: uma barra por mês a partir da linha do zero,
// para cima quando sobra, para baixo quando falta. Tocar numa barra mostra os
// totais daquele mês no topo.
//
// A cor nunca é o único sinal (verde e vermelho se confundem para daltônicos):
// o lado da linha e o texto "Sobra"/"Falta" dizem a mesma coisa.
//
// Mesmo padrão do TransactionSheet: <dialog> nativo, e o pai só renderiza
// enquanto está aberto (a busca roda de novo a cada abertura).
export function TrendSheet({ month, period, onClose }: {
  month: string // mês da tela: centro do gráfico e o selecionado de início
  period: PeriodResponse
  onClose: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [points, setPoints] = useState<MonthTrend[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState(month)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [])

  useEffect(() => {
    const { from, to } = trendRange(month, period)
    api<MonthTrend[]>(`/api/reports/monthly-trend?from=${from}&to=${to}`)
      .then(setPoints)
      .catch(e => setError(e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor'))
  }, [month, period])

  const scale = trendScale(points?.map(p => p.balance) ?? [])
  const current = points?.find(p => p.month === selected)

  return (
    <dialog
      ref={dialogRef}
      className="sheet"
      aria-labelledby="trend-title"
      onClose={onClose}
      onClick={e => {
        if (e.target === e.currentTarget) dialogRef.current?.close()
      }}
    >
      <div className="sheet-body">
        <div className="sheet-handle" aria-hidden="true" />
        <header className="sheet-header">
          <h2 id="trend-title">Evolução do saldo</h2>
          <button type="button" className="icon-btn" aria-label="Fechar"
                  onClick={() => dialogRef.current?.close()}>
            <Close />
          </button>
        </header>

        {error && <p className="error">{error}</p>}
        {!points && !error && <div className="skeleton" style={{ height: 300 }} aria-busy="true" />}

        {points && (
          <>
            {current && (
              <div className="trend-detail" aria-live="polite">
                <small className="trend-month">{formatMonth(current.month)}</small>
                <div className="trend-balance">
                  <strong>{formatMoney(current.balance)}</strong>
                  <span className={current.balance < 0 ? 'trend-tag down' : 'trend-tag up'}>
                    {current.balance < 0 ? 'Falta' : 'Sobra'}
                  </span>
                </div>
                <div className="trend-totals">
                  <small>Receitas <strong>{formatMoney(current.totalIncome)}</strong></small>
                  <small>Despesas <strong>{formatMoney(current.totalExpense)}</strong></small>
                </div>
              </div>
            )}

            <div className="trend-chart" role="group" aria-label="Saldo por mês">
              {points.map((p, i) => (
                // Sem o sinal no texto: "falta -R$ 800,00" diria a falta duas vezes.
                <button key={p.month} type="button" aria-pressed={p.month === selected}
                        className={p.month === selected ? 'trend-col selected' : 'trend-col'}
                        aria-label={`${formatMonth(p.month)}: ${p.balance < 0 ? 'falta' : 'sobra'} ${formatMoney(Math.abs(p.balance))}`}
                        onClick={() => setSelected(p.month)}>
                  {/* flexGrow: o espaço acima/abaixo do zero segue a escala; a linha do
                      zero é a borda de baixo de .trend-above. */}
                  <span className="trend-above" style={{ flexGrow: scale.above }}>
                    {p.balance > 0 && <span className="trend-bar up" style={{ height: `${barPercent(p.balance, scale)}%` }} />}
                  </span>
                  <span className="trend-below" style={{ flexGrow: scale.below }}>
                    {p.balance < 0 && <span className="trend-bar down" style={{ height: `${barPercent(p.balance, scale)}%` }} />}
                  </span>
                  <small className="trend-label">
                    {formatMonthShort(p.month)}
                    {/* O ano só no primeiro mês e quando vira (janeiro): "abr" aparece
                        duas vezes em 13 meses, e o ano diz qual é qual. */}
                    <span className="trend-year">{i === 0 || p.month.endsWith('-01') ? p.month.slice(0, 4) : ' '}</span>
                  </small>
                </button>
              ))}
            </div>
            <p className="trend-note">Acima da linha, sobra dinheiro no mês; abaixo, falta.</p>
          </>
        )}
      </div>
    </dialog>
  )
}
