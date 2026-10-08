import { useEffect, useRef, useState, type TouchEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError, clearToken } from '../api'
import {
  addMonths, currentMonth, formatDayHeading, formatMoney, formatMonth, formatPaymentMethod, isValidMonth,
  monthOf, todayIso, transactionTitle,
} from '../format'
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, LogOut, Plus, Tag, Wallet } from '../icons'
import { TransactionSheet } from '../TransactionSheet'
import type {
  MonthlySummaryResponse, PeriodResponse, TransactionResponse, TransactionType, UserResponse,
} from '../types'

interface MonthData {
  summary: MonthlySummaryResponse
  transactions: TransactionResponse[]
}

// Resultado da última busca, junto com o mês a que ele pertence.
type MonthResult = { month: string } & ({ data: MonthData } | { error: string })

// Painel fechado, aberto para criar, ou aberto editando uma transação.
type SheetState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; transaction: TransactionResponse }

type Filter = 'ALL' | TransactionType

// Gesto de arrastar: distância mínima, quanto mais horizontal que vertical
// (para não confundir com a rolagem da página) e duração máxima.
const SWIPE_MIN_PX = 60
const SWIPE_RATIO = 1.5
const SWIPE_MAX_MS = 700
// No Safari, arrastar a partir da borda é o "voltar" do navegador: ignoramos.
const EDGE_PX = 24

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
  const [filter, setFilter] = useState<Filter>('ALL')
  // Direção da última troca de mês: define de que lado o conteúdo novo entra.
  const [slide, setSlide] = useState<'next' | 'prev' | null>(null)
  // Ponto onde o dedo encostou. useRef (e não useState): mudar não precisa redesenhar a tela.
  const touchStart = useRef<{ x: number; y: number; time: number } | null>(null)

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
    if (delta < 0 && !canGoBack) return
    setSlide(delta > 0 ? 'next' : 'prev')
    setSearchParams({ month: addMonths(month, delta) })
  }

  function handleTouchStart(e: TouchEvent) {
    const touch = e.touches[0]
    // Com o painel aberto o gesto não vale (o toque dentro dele também chega aqui).
    const ignored = sheet.mode !== 'closed' || e.touches.length !== 1
      || touch.clientX < EDGE_PX || touch.clientX > window.innerWidth - EDGE_PX
    touchStart.current = ignored ? null : { x: touch.clientX, y: touch.clientY, time: Date.now() }
  }

  function handleTouchEnd(e: TouchEvent) {
    const start = touchStart.current
    touchStart.current = null
    if (!start) return
    const touch = e.changedTouches[0]
    const dx = touch.clientX - start.x
    const dy = touch.clientY - start.y
    const isSwipe = Math.abs(dx) >= SWIPE_MIN_PX
      && Math.abs(dx) >= Math.abs(dy) * SWIPE_RATIO
      && Date.now() - start.time <= SWIPE_MAX_MS
    if (!isSwipe) return
    // Dedo para a esquerda (dx negativo) = próximo mês, como virar página.
    goToMonth(dx < 0 ? 1 : -1)
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
      setSlide(null)
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
    <main onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
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

      {/* key={month}: a cada mês o bloco é recriado e a animação de entrada roda
          de novo, vindo do lado certo (próximo pela direita, anterior pela esquerda). */}
      <div key={month} className={slide ? `month-body slide-${slide}` : 'month-body'}>
        {!current && <MonthSkeleton />}
        {current && 'error' in current && <p className="error">{current.error}</p>}
        {current && 'data' in current && (
          <MonthContent
            data={current.data}
            highlightId={highlightId}
            editable={editable}
            filter={filter}
            onFilterChange={setFilter}
            onSelect={tx => setSheet({ mode: 'edit', transaction: tx })}
          />
        )}
      </div>

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

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'Todas' },
  { value: 'EXPENSE', label: 'Despesas' },
  { value: 'INCOME', label: 'Receitas' },
]

// Agrupa por dia mantendo a ordem da API (mais recentes primeiro):
// transações seguidas com a mesma data formam um grupo.
function groupByDay(list: TransactionResponse[]) {
  const groups: { date: string; items: TransactionResponse[] }[] = []
  for (const tx of list) {
    const last = groups.at(-1)
    if (last && last.date === tx.occurredOn) {
      last.items.push(tx)
    } else {
      groups.push({ date: tx.occurredOn, items: [tx] })
    }
  }
  return groups
}

function MonthContent({ data: { summary, transactions }, highlightId, editable, filter, onFilterChange, onSelect }: {
  data: MonthData
  highlightId: number | null
  editable: boolean
  filter: Filter
  onFilterChange: (f: Filter) => void
  onSelect: (tx: TransactionResponse) => void
}) {
  const visible = filter === 'ALL' ? transactions : transactions.filter(tx => tx.type === filter)
  const groups = groupByDay(visible)
  let rowIndex = 0 // para a entrada em cascata atravessar os grupos

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
        <div className="card-head">
          <h2>Transações</h2>
          <div className="segmented small" role="tablist" aria-label="Filtrar transações">
            {FILTERS.map(f => (
              <button key={f.value} type="button" role="tab" aria-selected={filter === f.value}
                      className={filter === f.value ? 'active' : ''} onClick={() => onFilterChange(f.value)}>
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {groups.length === 0 ? (
          <div className="empty">
            <Wallet />
            <p>
              {transactions.length === 0
                ? 'Nenhuma transação neste mês.'
                : filter === 'EXPENSE' ? 'Nenhuma despesa neste mês.' : 'Nenhuma receita neste mês.'}
            </p>
            {editable && transactions.length === 0 && <small>Toque no + para adicionar.</small>}
          </div>
        ) : (
          groups.map(group => (
            <div key={group.date} className="day-group">
              <h3 className="day-heading">{formatDayHeading(group.date)}</h3>
              <ul className="transactions">
                {group.items.map(tx => {
                  const kind = tx.type === 'INCOME' ? 'income' : 'expense'
                  // Linhas entram em cascata; o teto de 12 evita esperar demais em listas longas.
                  const delay = 200 + Math.min(rowIndex++, 12) * 40
                  const content = (
                    <>
                      <span className={`tx-icon ${kind}`}>
                        {tx.type === 'INCOME' ? <ArrowUp /> : <ArrowDown />}
                      </span>
                      <div className="tx-main">
                        <strong>{transactionTitle(tx)}</strong>
                        <small>
                          {tx.categoryName ?? 'Sem categoria'}
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
            </div>
          ))
        )}
      </section>
    </>
  )
}
