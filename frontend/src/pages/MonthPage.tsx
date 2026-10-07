import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError, clearToken } from '../api'
import {
  addMonths, currentMonth, formatDay, formatMoney, formatMonth, formatPaymentMethod, isValidMonth,
  monthOf, todayIso,
} from '../format'
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, LogOut, Plus, Tag, Wallet } from '../icons'
import { TransactionSheet } from '../TransactionSheet'
import type {
  MonthlySummaryResponse, PeriodResponse, TransactionResponse, UserResponse,
} from '../types'

interface MonthData {
  summary: MonthlySummaryResponse
  transactions: TransactionResponse[]
}

// Resultado da última busca, junto com o mês a que ele pertence.
type MonthResult = { month: string } & ({ data: MonthData } | { error: string })

// Painel fechado, aberto para criar, ou aberto editando uma transação.
type SheetState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; transaction: TransactionResponse }

export function MonthPage() {
  const navigate = useNavigate()
  // O mês fica na URL (/?month=2026-09): sobrevive ao F5 e ao botão "voltar".
  const [searchParams, setSearchParams] = useSearchParams()
  const monthParam = searchParams.get('month')
  const month = isValidMonth(monthParam) ? monthParam : currentMonth()

  const [user, setUser] = useState<UserResponse | null>(null)
  const [period, setPeriod] = useState<PeriodResponse | null>(null)
  const [result, setResult] = useState<MonthResult | null>(null)
  // Incrementar força o efeito de busca a rodar de novo para o MESMO mês.
  const [reloadKey, setReloadKey] = useState(0)
  const [sheet, setSheet] = useState<SheetState>({ mode: 'closed' })
  // Transação recém-criada ou editada, destacada por um instante na lista.
  const [highlightId, setHighlightId] = useState<number | null>(null)

  // Derivado, não armazenado: se o resultado é de outro mês, o atual ainda está
  // carregando. Assim não precisamos "zerar" o estado ao trocar de mês.
  const current = result?.month === month ? result : null

  // Limites vêm do backend (GET /api/period). Meses "2026-10" comparam como texto.
  // Enquanto o período não chega, nada é editável (o "+" só aparece depois).
  const editable = period !== null && month >= period.firstEditableMonth
  const canGoBack = period === null || month > period.oldestVisibleMonth

  useEffect(() => {
    api<UserResponse>('/api/me').then(setUser).catch(() => {})
    api<PeriodResponse>('/api/period').then(setPeriod).catch(() => {})
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
    setSheet({ mode: 'closed' })
    setHighlightId(tx.id)
    const target = monthOf(tx.occurredOn)
    if (target !== month) {
      setSearchParams({ month: target })
    } else {
      setReloadKey(k => k + 1)
    }
  }

  function handleDeleted() {
    setSheet({ mode: 'closed' })
    setReloadKey(k => k + 1)
  }

  // Nova transação olhando outro mês (ex.: planejando dezembro): começa no dia 1º dele.
  const defaultDate = month === currentMonth() ? todayIso() : `${month}-01`

  return (
    <main>
      <header className="top">
        <span className="avatar">{user?.name.charAt(0).toUpperCase() ?? ''}</span>
        <div className="greeting">
          <small>Olá,</small>
          <strong>{user?.name ?? '...'}</strong>
        </div>
        <Link to="/categorias" className="icon-btn" aria-label="Categorias" title="Categorias">
          <Tag />
        </Link>
        <button className="icon-btn" onClick={handleLogout} aria-label="Sair" title="Sair">
          <LogOut />
        </button>
      </header>

      <nav className="month-nav">
        <button className="icon-btn" onClick={() => goToMonth(-1)} aria-label="Mês anterior"
                disabled={!canGoBack}>
          <ChevronLeft />
        </button>
        <div className="month-title">
          <h1>{formatMonth(month)}</h1>
          {period && !editable && <span className="badge">Somente leitura</span>}
        </div>
        <button className="icon-btn" onClick={() => goToMonth(1)} aria-label="Próximo mês">
          <ChevronRight />
        </button>
      </nav>

      {!current && <MonthSkeleton />}
      {current && 'error' in current && <p className="error">{current.error}</p>}
      {current && 'data' in current && (
        <MonthContent
          data={current.data}
          highlightId={highlightId}
          editable={editable}
          onSelect={tx => setSheet({ mode: 'edit', transaction: tx })}
        />
      )}

      {editable && (
        <button className="fab" onClick={() => setSheet({ mode: 'create' })} aria-label="Nova transação"
                title="Nova transação">
          <Plus />
        </button>
      )}

      {sheet.mode !== 'closed' && period && (
        <TransactionSheet
          transaction={sheet.mode === 'edit' ? sheet.transaction : undefined}
          defaultDate={defaultDate}
          period={period}
          onClose={() => setSheet({ mode: 'closed' })}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
        />
      )}
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

function MonthContent({ data: { summary, transactions }, highlightId, editable, onSelect }: {
  data: MonthData
  highlightId: number | null
  editable: boolean
  onSelect: (tx: TransactionResponse) => void
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
            {editable && <small>Toque no + para adicionar.</small>}
          </div>
        ) : (
          <ul className="transactions">
            {transactions.map((tx, i) => {
              const kind = tx.type === 'INCOME' ? 'income' : 'expense'
              // Linhas entram em cascata; o teto de 12 evita esperar demais em listas longas.
              const delay = 200 + Math.min(i, 12) * 40
              const content = (
                <>
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
                </>
              )
              return (
                <li key={tx.id} className={tx.id === highlightId ? 'reveal just-added' : 'reveal'}
                    style={{ animationDelay: `${delay}ms` }}>
                  {/* Mês aberto: a linha é um <button> (teclado e leitor de tela funcionam).
                      Mês fechado: só texto, nada para clicar. */}
                  {editable ? (
                    <button type="button" className="tx-row" onClick={() => onSelect(tx)}>
                      {content}
                    </button>
                  ) : (
                    <div className="tx-row">{content}</div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </>
  )
}
