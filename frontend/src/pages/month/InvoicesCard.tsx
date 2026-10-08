import { formatMoney } from '../../format'
import { CreditCard } from '../../icons'
import type { CardTotal } from '../../types'

// Faturas do mês, uma por cartão. Tocar numa fatura filtra a lista de
// transações (quem guarda a escolha é a tela, que também passa para a lista).
export function InvoicesCard({ invoices, selected, onToggle }: {
  invoices: CardTotal[] // já ordenadas pelo backend, da maior para a menor
  selected: CardTotal | null
  onToggle: (invoice: CardTotal) => void
}) {
  if (invoices.length === 0) {
    return null
  }

  return (
    <section className="card reveal" style={{ animationDelay: '120ms' }}>
      <h2>Faturas do mês</h2>
      <ul className="transactions">
        {invoices.map(invoice => {
          // cardId null ("Crédito sem cartão") compara igual a null: funciona para as duas.
          const isSelected = selected !== null && selected.cardId === invoice.cardId
          return (
            <li key={invoice.cardId ?? 'none'}>
              <button type="button" className={isSelected ? 'tx-row selected' : 'tx-row'}
                      aria-pressed={isSelected} onClick={() => onToggle(invoice)}>
                <span className="tx-icon"><CreditCard /></span>
                <div className="tx-main">
                  <strong>{invoice.cardName}</strong>
                  <small>{invoice.purchases} {invoice.purchases === 1 ? 'compra' : 'compras'}</small>
                </div>
                <span className="tx-amount">{formatMoney(invoice.total)}</span>
              </button>
            </li>
          )
        })}
      </ul>
      <p className="hint">
        As compras já estão nos gastos do mês. Não lance o pagamento da fatura como despesa:
        ele seria contado duas vezes.
      </p>
    </section>
  )
}
