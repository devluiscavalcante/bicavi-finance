import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api, ApiError, clearToken } from '../api'
import {
  addMonths, currentMonth, formatDay, formatMoney, formatMonth, formatPaymentMethod, isValidMonth,
  monthOf,
} from '../format'
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, LogOut, Plus, Wallet } from '../icons'
import { TransactionSheet } from '../TransactionSheet'
import type { MonthlySummaryResponse, TransactionResponse, UserResponse } from '../types'

interface MonthData {
  summary: MonthlySummaryResponse
  transactions: TransactionResponse[]
}

// Resultado da última busca, junto com o mês a que ele pertence.
type MonthResult = { month: string } & ({ data: MonthData } | { error: string })

export function MonthPage() {
  const navigate = useNavigate()
  // O mês fica na URL (/?month=2026-09): sobrevive ao F5 e ao botão "voltar".
  const [searchParams, setSearchParams] = useSearchParams()
  const monthParam = searchParams.get('month')
  const month = isValidMonth(monthParam) ? monthParam : currentMonth()

  const [user, setUser] = useState<UserResponse | null>(null)
  const [result, setResult] = useState<MonthResult | null>(null)
  // Incrementar força o efeito de busca a rodar de novo para o MESMO mês.
  const [reloadKey, setReloadKey] = useState(0)
  const [sheetOpen, setSheetOpen] = useState(false)
  // Transação recém-criada, destacada por um instante na lista.
  const [highlightId, setHighlightId] = useState<number | null>(null)

  // Derivado, não armazenado: se o resultado é de outro mês, o atual ainda está
  // carregando. Assim não precisamos "zerar" o estado ao trocar de mês.
  const current = result?.month === month ? result : null

  useEffect(() => {
    api<UserResponse>('/api/me').then(setUser).catch(() => {})
  }, [])

  useEffect(() => {
    // Se o usuário trocar de mês rápido, a resposta do mês anterior pode chegar
    // DEPOIS da do mês novo. A flag descarta respostas de um mês que já saiu da tela.
    let ignore = false

    // As duas chamadas são independentes: Promise.all faz as duas ao mesmo tempo.
    Promise.all([
      api<MonthlySummaryResponse>(`/api/reports/monthly-summary?month=${month}`),
      api<TransactionResponse[]>(`/api/transactions?month=${month}`),
    ])
      .then(([summary, transactions]) => {
        if (!ignore) setResult({ month, data: { summary, transactions } })
      })
      .catch(e => {
        const error = e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor'
        if (!ignore) setResult({ month, error })
      })

    return () => {
      ignore = true
    }
  }, [month, reloadKey])

  function goToMonth(delta: number) {
    setSearchParams({ month: addMonths(month, delta) })
  }

  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  // Depois de salvar, mostra o mês DA TRANSAÇÃO (pode ser outro, se a data foi
  // alterada). Os totais vêm de novo da API: o frontend não soma nada.
  function handleSaved(tx: TransactionResponse) {
    setSheetOpen(false)
    setHighlightId(tx.id)
    const target = monthOf(tx.occurredOn)
    if (target !== month) {
      setSearchParams({ month: target })
    } else {
      setReloadKey(k => k + 1)
    }
  }

  return (
    <main>
      <header className="top">
        <span className="avatar">{user?.name.charAt(0).toUpperCase() ?? ''}</span>
        <div className="greeting">
          <small>Olá,</small>
          <strong>{user?.name ?? '...'}</strong>
        </div>
        <button className="icon-btn" onClick={handleLogout} aria-label="Sair" title="Sair">
          <LogOut />
        </button>
      </header>

      <nav className="month-nav">
        <button className="icon-btn" onClick={() => goToMonth(-1)} aria-label="Mês anterior">
          <ChevronLeft />
        </button>
        <h1>{formatMonth(month)}</h1>
        <button className="icon-btn" onClick={() => goToMonth(1)} aria-label="Próximo mês">
          <ChevronRight />
        </button>
      </nav>

      {!current && <MonthSkeleton />}
      {current && 'error' in current && <p className="error">{current.error}</p>}
      {current && 'data' in current && <MonthContent data={current.data} highlightId={highlightId} />}

      <button className="fab" onClick={() => setSheetOpen(true)} aria-label="Nova transação"
              title="Nova transação">
        <Plus />
      </button>

      {sheetOpen && <TransactionSheet onClose={() => setSheetOpen(false)} onSaved={handleSaved} />}
    </main>
  )
}

function MonthSkeleton() {
  return (
    <div aria-label="Carregando" aria-busy="true">
      <div className="skeleton" style={{ height: 172, borderRadius: 24 }} />
      <div className="skeleton" style={{ height: 220, marginTop: 16 }} />
    </div>
  )
}

function MonthContent({ data: { summary, transactions }, highlightId }: {
  data: MonthData
  highlightId: number | null
}) {
  return (
    <>
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

      {summary.expensesByCategory.length > 0 && (
        <section className="card reveal" style={{ animationDelay: '80ms' }}>
          <h2>Gastos por categoria</h2>
          <ul className="categories">
            {summary.expensesByCategory.map(c => (
              // categoryId null ("Sem categoria") aparece no máximo uma vez.
              <li key={c.categoryId ?? 'none'}>
                <div className="category-head">
                  <span>{c.categoryName ?? 'Sem categoria'}</span>
                  <strong>{formatMoney(c.total)}</strong>
                </div>
                <div className="bar">
                  {/* Proporção só para a largura da barra; nenhum valor exibido vem daqui. */}
                  <span style={{ width: `${(c.total / summary.totalExpense) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="card reveal" style={{ animationDelay: '160ms' }}>
        <h2>Transações</h2>
        {transactions.length === 0 ? (
          <div className="empty">
            <Wallet />
            <p>Nenhuma transação neste mês.</p>
            <small>Toque no + para adicionar.</small>
          </div>
        ) : (
          <ul className="transactions">
            {transactions.map((tx, i) => {
              const kind = tx.type === 'INCOME' ? 'income' : 'expense'
              // Linhas entram em cascata; o teto de 12 evita esperar demais em listas longas.
              const delay = 200 + Math.min(i, 12) * 40
              return (
                <li key={tx.id} className={tx.id === highlightId ? 'reveal just-added' : 'reveal'}
                    style={{ animationDelay: `${delay}ms` }}>
                  <span className={`tx-icon ${kind}`}>
                    {tx.type === 'INCOME' ? <ArrowUp /> : <ArrowDown />}
                  </span>
                  <div className="tx-main">
                    <strong>
                      {tx.description ?? tx.categoryName ?? (tx.type === 'INCOME' ? 'Receita' : 'Despesa')}
                    </strong>
                    <small>
                      {formatDay(tx.occurredOn)} · {tx.categoryName ?? 'Sem categoria'}
                      {tx.paymentMethod && ` · ${formatPaymentMethod(tx.paymentMethod)}`}
                    </small>
                  </div>
                  <span className={`tx-amount ${kind}`}>
                    {tx.type === 'INCOME' ? '+' : '−'} {formatMoney(tx.amount)}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </>
  )
}
