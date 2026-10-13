import { formatMoney } from '../../format'
import { CreditCard } from '../../icons'
import type { CardTotal } from '../../types'
import { PayToggle } from './PayToggle'

// Faturas do mês, uma por cartão. Tocar numa fatura filtra a lista de
// transações (quem guarda a escolha é a tela, que também passa para a lista).
// O círculo ao lado paga (ou desmarca) todas as compras da fatura de uma vez.
export function InvoicesCard({ invoices, selected, onToggle, editable, busy, onTogglePaid }: {
  invoices: CardTotal[] // já ordenadas pelo backend, da maior para a menor
  selected: CardTotal | null
  onToggle: (invoice: CardTotal) => void
  editable: boolean
  busy: boolean
  onTogglePaid: (invoice: CardTotal) => void
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
          const paid = invoice.toPay === 0
          return (
            <li key={invoice.cardId ?? 'none'} className="tx-line">
              <button type="button" className={isSelected ? 'tx-row selected' : 'tx-row'}
                      aria-pressed={isSelected} onClick={() => onToggle(invoice)}>
                <span className="tx-icon"><CreditCard /></span>
                <div className="tx-main">
                  <strong>{invoice.cardName}</strong>
                  <small>
                    {invoice.purchases} {invoice.purchases === 1 ? 'compra' : 'compras'}
                    {' · '}
                    {paid ? 'Paga' : invoice.toPay === invoice.total ? 'A pagar' : `${formatMoney(invoice.toPay)} a pagar`}
                  </small>
                </div>
                <span className={paid ? 'tx-amount paid' : 'tx-amount'}>{formatMoney(invoice.total)}</span>
              </button>
              {/* "Crédito sem cartão" não tem um cartão para pagar de uma vez:
                  essas compras antigas são marcadas uma a uma na lista. */}
              {invoice.cardId !== null && (
                <PayToggle
                  paid={paid}
                  label={paid ? `Desmarcar pagamento da fatura ${invoice.cardName}`
                    : `Marcar fatura ${invoice.cardName} como paga`}
                  busy={busy}
                  onToggle={editable ? () => onTogglePaid(invoice) : undefined}
                />
              )}
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
