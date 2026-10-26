import { useEffect, useRef, useState, type MouseEvent, type PointerEvent, type TouchEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError, clearToken } from '../api'
import { addMonths, currentMonth, formatMonth, isValidMonth, monthOf, todayIso } from '../format'
import { ChevronLeft, ChevronRight, LogOut, Plus, Tag } from '../icons'
import { cachedMonth, clearMonthCache, fetchMonth, prefetchMonth, type MonthData, type MonthResult } from '../monthCache'
import { MonthPicker } from '../MonthPicker'
import { PullToRefresh } from '../PullToRefresh'
import { TransactionSheet } from '../TransactionSheet'
import type { CardTotal, PaymentRequest, PeriodResponse, TransactionResponse, UserResponse } from '../types'
import { CategoryBreakdown } from './month/CategoryBreakdown'
import { InvoicesCard } from './month/InvoicesCard'
import { MonthHero } from './month/MonthHero'
import { TransactionList, type Filter } from './month/TransactionList'
import { TrendSheet } from './month/TrendSheet'

// Painel fechado, aberto para criar, ou aberto editando uma transação.
type SheetState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; transaction: TransactionResponse }

// Gesto de arrastar: distância mínima, quanto mais horizontal que vertical
// (para não confundir com a rolagem da página) e duração máxima.
const SWIPE_MIN_PX = 60
const SWIPE_RATIO = 1.5
const SWIPE_MAX_MS = 700
// No Safari, arrastar a partir da borda é o "voltar" do navegador: ignoramos.
const EDGE_PX = 24
// Segurar o título do mês por esse tempo abre o carrossel. Mexer o dedo mais
// que LONG_PRESS_SLOP_PX antes disso cancela (era rolagem ou swipe, não "segurar").
const LONG_PRESS_MS = 450
const LONG_PRESS_SLOP_PX = 10

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
  // Carrossel de meses: null = fechado; senão, a altura (px) onde ele abre e o
  // dedo que o abriu (continua arrastando a faixa sem soltar; null pelo teclado).
  const [picker, setPicker] = useState<{ top: number; dragPointerId: number | null } | null>(null)
  const longPress = useRef<{ x: number; y: number; timer: number } | null>(null)
  // Painel de evolução do saldo (tocar no saldo do mês).
  const [trendOpen, setTrendOpen] = useState(false)
  const titleRef = useRef<HTMLButtonElement>(null)

  // No iPhone, segurar o dedo inicia a seleção de texto do iOS, e arrastar depois
  // estende a seleção em vez de girar o carrossel. preventDefault no touchstart
  // desliga esse gesto nativo (e o menu "Copiar"). Precisa ser um listener nativo
  // com passive: false: o onTouchStart do React é passivo e ignora o preventDefault.
  useEffect(() => {
    const title = titleRef.current
    if (!title) return
    const block = (e: globalThis.TouchEvent) => e.preventDefault()
    title.addEventListener('touchstart', block, { passive: false })
    return () => title.removeEventListener('touchstart', block)
  }, [])

  // Derivado, não armazenado: se o resultado é de outro mês, o atual ainda está
  // carregando. Assim não precisamos "zerar" o estado ao trocar de mês.
  // Enquanto carrega, mostra o que estiver no cache (mês já visto ou pré-carregado);
  // sem cache, o skeleton. A busca nova substitui quando chegar.
  const cached = cachedMonth(month)
  const current: MonthResult | null = result?.month === month ? result : cached ? { month, data: cached } : null
  const hasData = current !== null && 'data' in current

  // Limites vêm do backend (GET /api/period). Meses "2026-10" comparam como texto.
  // Enquanto o período não chega, nada é editável (o "+" só aparece depois).
  const editable = period !== null && month >= period.firstEditableMonth
  const canGoBack = period === null || month > period.oldestVisibleMonth
  const canGoForward = period === null || month < period.lastMonth

  useEffect(() => {
    api<UserResponse>('/api/me').then(setUser).catch(() => {})
    api<PeriodResponse>('/api/period').then(setPeriod).catch(() => {})
  }, [])

  useEffect(() => {
    // Se o usuário trocar de mês rápido, a resposta do mês anterior pode chegar
    // DEPOIS da do mês novo. A flag descarta respostas de um mês que já saiu da tela.
    let ignore = false

    fetchMonth(month).then(loaded => {
      if (!ignore) setResult(loaded)
    })

    return () => {
      ignore = true
    }
  }, [month, reloadKey])

  // Com o mês na tela, já busca o anterior e o próximo (se estiverem nos limites):
  // swipe e setas abrem na hora. prefetchMonth não repete o que já tem ou já está buscando.
  useEffect(() => {
    if (!period || !hasData) return
    if (month > period.oldestVisibleMonth) prefetchMonth(addMonths(month, -1))
    if (month < period.lastMonth) prefetchMonth(addMonths(month, 1))
  }, [month, period, hasData])

  // Depois de qualquer alteração: limpa o cache (outros meses podem ter mudado,
  // ex.: parcelas) e busca de novo o mês da tela.
  function reloadAll() {
    clearMonthCache()
    setReloadKey(k => k + 1)
  }

  // Puxar para atualizar: busca de novo o mês E o período (o app pode ficar
  // aberto de um dia para o outro, e o primeiro mês editável mudar).
  // Os dados antigos ficam na tela até os novos chegarem.
  async function refresh() {
    clearMonthCache()
    const [loaded, newPeriod] = await Promise.all([
      fetchMonth(month),
      api<PeriodResponse>('/api/period').catch(() => null),
    ])
    setResult(loaded)
    if (newPeriod) setPeriod(newPeriod)
  }

  function goToMonth(delta: number) {
    if (delta < 0 && !canGoBack) return
    if (delta > 0 && !canGoForward) return
    setSlide(delta > 0 ? 'next' : 'prev')
    setSearchParams({ month: addMonths(month, delta) })
  }

  // Salto direto para um mês escolhido no carrossel.
  function jumpToMonth(target: string) {
    setPicker(null)
    if (target === month) return
    setSlide(target > month ? 'next' : 'prev')
    setSearchParams({ month: target })
  }

  function openPicker(title: HTMLElement, dragPointerId: number | null) {
    // Sem os limites do período não dá para montar a lista de meses.
    if (!period) return
    setPicker({ top: title.getBoundingClientRect().bottom + 8, dragPointerId })
  }

  function cancelLongPress() {
    if (longPress.current) clearTimeout(longPress.current.timer)
    longPress.current = null
  }

  function handleTitlePointerDown(e: PointerEvent<HTMLButtonElement>) {
    cancelLongPress()
    const title = e.currentTarget
    const pointerId = e.pointerId
    const timer = window.setTimeout(() => {
      longPress.current = null
      openPicker(title, pointerId)
    }, LONG_PRESS_MS)
    longPress.current = { x: e.clientX, y: e.clientY, timer }
  }

  function handleTitlePointerMove(e: PointerEvent) {
    const start = longPress.current
    if (start && Math.hypot(e.clientX - start.x, e.clientY - start.y) > LONG_PRESS_SLOP_PX) cancelLongPress()
  }

  // Pelo teclado (Enter/Espaço no título) não existe "segurar": o click já abre.
  // detail === 0 indica click que não veio de mouse nem de toque.
  function handleTitleClick(e: MouseEvent<HTMLButtonElement>) {
    if (e.detail === 0) openPicker(e.currentTarget, null)
  }

  function handleTouchStart(e: TouchEvent) {
    const touch = e.touches[0]
    // Com o painel ou o carrossel aberto o gesto não vale (o toque dentro deles também chega aqui).
    const ignored = sheet.mode !== 'closed' || picker !== null || trendOpen || e.touches.length !== 1
      || touch.clientX < EDGE_PX || touch.clientX > window.innerWidth - EDGE_PX
    touchStart.current = ignored ? null : { x: touch.clientX, y: touch.clientY, time: Date.now() }
  }

  function handleTouchEnd(e: TouchEvent) {
    const start = touchStart.current
    touchStart.current = null
    // O toque que abriu o carrossel (segurando o título) começou com ele fechado;
    // soltar o dedo depois de girar a faixa não pode virar swipe de trocar mês.
    if (!start || picker !== null) return
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
    clearMonthCache()
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
      clearMonthCache()
      setSlide(null)
      setSearchParams({ month: target })
    } else {
      reloadAll()
    }
  }

  function handleDeleted() {
    setSheet({ mode: 'closed' })
    reloadAll()
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
          {/* Segurar abre o carrossel de meses. onContextMenu: no Android, segurar
              abriria o menu do navegador em vez disso. */}
          <button ref={titleRef} type="button" className="month-title-btn" aria-haspopup="dialog"
                  title="Segure para escolher o mês"
                  onPointerDown={handleTitlePointerDown} onPointerMove={handleTitlePointerMove}
                  onPointerUp={cancelLongPress} onPointerCancel={cancelLongPress}
                  onPointerLeave={cancelLongPress} onClick={handleTitleClick}
                  onContextMenu={e => e.preventDefault()}>
            <h1>{formatMonth(month)}</h1>
          </button>
          {period && !editable && <span className="badge">Somente leitura</span>}
        </div>
        <button className="icon-btn" onClick={() => goToMonth(1)} aria-label="Próximo mês"
                disabled={!canGoForward}>
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
            onChanged={reloadAll}
            onOpenTrend={() => setTrendOpen(true)}
          />
        )}
      </div>

      <PullToRefresh onRefresh={refresh} disabled={sheet.mode !== 'closed' || picker !== null || trendOpen} />

      {picker && period && (
        <MonthPicker
          month={month}
          from={period.oldestVisibleMonth}
          to={period.lastMonth}
          top={picker.top}
          dragPointerId={picker.dragPointerId}
          onSelect={jumpToMonth}
          onClose={() => setPicker(null)}
        />
      )}

      {trendOpen && period && (
        <TrendSheet month={month} period={period} onClose={() => setTrendOpen(false)} />
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

// Conteúdo do mês já carregado. Coordena o que é compartilhado entre os blocos:
// a fatura escolhida aparece selecionada nas faturas E filtra a lista.
// Cada bloco cuida do próprio estado ("Ver todas", "Ver mais").
function MonthContent({
  data: { summary, transactions }, highlightId, editable, filter, onFilterChange, onSelect, onChanged, onOpenTrend,
}: {
  data: MonthData
  highlightId: number | null
  editable: boolean
  filter: Filter
  onFilterChange: (f: Filter) => void
  onSelect: (tx: TransactionResponse) => void
  onChanged: () => void // algo mudou no backend: a tela busca o mês de novo
  onOpenTrend: () => void
}) {
  // Fatura tocada: a lista mostra só as compras daquele cartão (para conferir com o banco).
  const [cardFilter, setCardFilter] = useState<CardTotal | null>(null)
  const listRef = useRef<HTMLElement>(null)
  // Pagamento sendo marcado: trava os círculos até a resposta (evita o toque duplo).
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)

  // Marca ou desmarca e busca o mês de novo: os totais (pago, a pagar, faturas)
  // vêm recalculados da API, o frontend não soma nada.
  async function sendPayment(path: string, paid: boolean) {
    setPaying(true)
    setPayError(null)
    try {
      const body: PaymentRequest = { paid }
      await api(path, { method: 'PATCH', body: JSON.stringify(body) })
      onChanged()
    } catch (e) {
      setPayError(e instanceof ApiError ? e.message : 'Não foi possível conectar ao servidor')
    } finally {
      setPaying(false)
    }
  }

  function toggleTransactionPaid(tx: TransactionResponse) {
    sendPayment(`/api/transactions/${tx.id}/payment`, tx.paidOn === null)
  }

  // Fatura com algo a pagar: paga tudo. Já paga: desmarca tudo.
  function toggleInvoicePaid(invoice: CardTotal) {
    sendPayment(`/api/cards/${invoice.cardId}/invoices/${summary.month}/payment`, invoice.toPay > 0)
  }

  // Trocar o filtro de tipo desfaz o filtro de fatura.
  function changeFilter(f: Filter) {
    setCardFilter(null)
    onFilterChange(f)
  }

  // Tocar na fatura já selecionada desfaz o filtro.
  function toggleCardFilter(card: CardTotal) {
    const same = cardFilter !== null && cardFilter.cardId === card.cardId
    setCardFilter(same ? null : card)
    onFilterChange('ALL')
    if (!same) {
      listRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  return (
    <>
      <MonthHero summary={summary} onOpenTrend={onOpenTrend} />
      <CategoryBreakdown categories={summary.expensesByCategory} totalExpense={summary.totalExpense} />
      {payError && <p className="error" role="alert" style={{ marginTop: 16 }}>{payError}</p>}
      <InvoicesCard invoices={summary.expensesByCard} selected={cardFilter} onToggle={toggleCardFilter}
                    editable={editable} busy={paying} onTogglePaid={toggleInvoicePaid} />
      <TransactionList
        transactions={transactions}
        filter={filter}
        onFilterChange={changeFilter}
        cardFilter={cardFilter}
        onClearCardFilter={() => setCardFilter(null)}
        highlightId={highlightId}
        editable={editable}
        onSelect={onSelect}
        sectionRef={listRef}
        busy={paying}
        onTogglePaid={toggleTransactionPaid}
      />
    </>
  )
}
