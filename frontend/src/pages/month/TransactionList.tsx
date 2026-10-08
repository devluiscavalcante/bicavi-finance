import { useState, type RefObject } from 'react'
import { formatDayHeading, formatMoney, formatPaymentMethod, transactionTitle } from '../../format'
import { ArrowDown, ArrowUp, Close, Wallet } from '../../icons'
import { groupByDay } from '../../lists'
import type { CardTotal, TransactionResponse, TransactionType } from '../../types'

export type Filter = 'ALL' | TransactionType

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'Todas' },
  { value: 'EXPENSE', label: 'Despesas' },
  { value: 'INCOME', label: 'Receitas' },
]

// Quantas transações aparecem de início, e a mais a cada "Ver mais".
const TX_PAGE = 5

// Lista do mês agrupada por dia, com filtro por tipo, filtro por fatura e "Ver mais".
// Os filtros vêm de fora (a tela também mostra as faturas); o "Ver mais" é só daqui.
export function TransactionList({
  transactions, filter, onFilterChange, cardFilter, onClearCardFilter, highlightId, editable, onSelect, sectionRef,
}: {
  transactions: TransactionResponse[]
  filter: Filter
  onFilterChange: (f: Filter) => void
  cardFilter: CardTotal | null // fatura tocada: só as compras daquele cartão
  onClearCardFilter: () => void
  highlightId: number | null // recém-salva: destacada e garantidamente visível
  editable: boolean
  onSelect: (tx: TransactionResponse) => void
  sectionRef: RefObject<HTMLElement | null> // a tela rola até aqui ao escolher uma fatura
}) {
  const [txLimit, setTxLimit] = useState(TX_PAGE)
  // Último destaque já revelado (ver abaixo).
  const [revealedId, setRevealedId] = useState<number | null>(null)
  // Filtros da última renderização (ver abaixo).
  const filterKey = `${filter}|${cardFilter === null ? '-' : cardFilter.cardId}`
  const [seenFilterKey, setSeenFilterKey] = useState(filterKey)

  // Os dois ajustes abaixo mudam o estado DURANTE a renderização, e não num
  // useEffect: assim a tela nunca é desenhada no estado errado para logo depois
  // ser redesenhada (é o padrão que a documentação do React recomenda para
  // "ajustar estado quando uma prop muda").

  // Filtro mudou (aqui ou nas faturas): a lista volta a mostrar só o começo.
  if (filterKey !== seenFilterKey) {
    setSeenFilterKey(filterKey)
    setTxLimit(TX_PAGE)
  }

  const byType = filter === 'ALL' ? transactions : transactions.filter(tx => tx.type === filter)
  // cardId null === null: a fatura "Crédito sem cartão" filtra as compras antigas sem cartão.
  const filtered = cardFilter === null ? byType
    : byType.filter(tx => tx.paymentMethod === 'CREDITO' && tx.cardId === cardFilter.cardId)

  // Transação recém-salva com data antiga pode cair depois do limite: a lista
  // abre até ela, uma vez. Só age quando ela já está na lista: a resposta da
  // API chega depois do destaque.
  const highlightIndex = filtered.findIndex(tx => tx.id === highlightId)
  if (highlightId !== revealedId && highlightIndex !== -1) {
    setRevealedId(highlightId)
    if (highlightIndex >= txLimit) {
      setTxLimit(Math.ceil((highlightIndex + 1) / TX_PAGE) * TX_PAGE)
    }
  }

  // Corta ANTES de agrupar por dia: os títulos de dia continuam corretos.
  const groups = groupByDay(filtered.slice(0, txLimit))
  const hiddenCount = Math.max(filtered.length - txLimit, 0)

  function showLess() {
    setTxLimit(TX_PAGE)
    // A lista encolhe: sem isso, quem estava no fim ficaria olhando para o vazio.
    sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  let rowIndex = 0 // para a entrada em cascata atravessar os grupos

  return (
    <section ref={sectionRef} className="card reveal" style={{ animationDelay: '160ms' }}>
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

      {cardFilter && (
        <button type="button" className="filter-chip" onClick={onClearCardFilter}
                aria-label={`Remover filtro da fatura ${cardFilter.cardName}`}>
          Fatura {cardFilter.cardName}
          <Close />
        </button>
      )}

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
                // Linhas entram em cascata. As que chegam pelo "Ver mais" recomeçam a
                // cascata do zero, sem esperar as linhas que já estavam na tela.
                const index = rowIndex++
                const delay = index < TX_PAGE ? 200 + index * 40 : (index % TX_PAGE) * 40
                return (
                  <li key={tx.id} className={tx.id === highlightId ? 'reveal just-added' : 'reveal'}
                      style={{ animationDelay: `${delay}ms` }}>
                    {/* Mês aberto: a linha é um <button> (teclado e leitor de tela funcionam).
                        Mês fechado: só texto, nada para clicar. */}
                    {editable ? (
                      <button type="button" className="tx-row" onClick={() => onSelect(tx)}>
                        <TransactionRow tx={tx} />
                      </button>
                    ) : (
                      <div className="tx-row"><TransactionRow tx={tx} /></div>
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
        ))
      )}

      {hiddenCount > 0 ? (
        <button type="button" className="more-btn" onClick={() => setTxLimit(l => l + TX_PAGE)}>
          Ver mais ({hiddenCount} {hiddenCount === 1 ? 'restante' : 'restantes'})
        </button>
      ) : txLimit > TX_PAGE && filtered.length > TX_PAGE && (
        <button type="button" className="more-btn" onClick={showLess}>
          Ver menos
        </button>
      )}
    </section>
  )
}

// Conteúdo de uma linha: ícone, título, "categoria · forma · cartão" e valor.
function TransactionRow({ tx }: { tx: TransactionResponse }) {
  const kind = tx.type === 'INCOME' ? 'income' : 'expense'
  return (
    <>
      <span className={`tx-icon ${kind}`}>
        {tx.type === 'INCOME' ? <ArrowUp /> : <ArrowDown />}
      </span>
      <div className="tx-main">
        <strong>{transactionTitle(tx)}</strong>
        <small>
          {tx.categoryName ?? 'Sem categoria'}
          {tx.paymentMethod && ` · ${formatPaymentMethod(tx.paymentMethod)}`}
          {tx.cardName && ` · ${tx.cardName}`}
        </small>
      </div>
      <span className={`tx-amount ${kind}`}>
        {tx.type === 'INCOME' ? '+' : '−'} {formatMoney(tx.amount)}
      </span>
    </>
  )
}
