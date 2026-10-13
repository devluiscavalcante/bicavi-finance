import { Check } from '../../icons'

// Círculo de "paga": cheio = paga, vazio = a pagar.
// Sem onToggle (mês somente leitura) só mostra o estado, sem botão.
export function PayToggle({ paid, label, title, busy = false, onToggle }: {
  paid: boolean
  label: string // o que o toque faz, para leitor de tela ("Marcar ... como paga")
  title?: string // dica ao passar o mouse ("Paga em 03/11")
  busy?: boolean // chamada em andamento: evita o toque duplo
  onToggle?: () => void
}) {
  const className = paid ? 'pay-toggle paid' : 'pay-toggle'

  if (!onToggle) {
    return (
      <span className={className} role="img" aria-label={paid ? 'Paga' : 'A pagar'} title={title}>
        <Check />
      </span>
    )
  }

  return (
    <button type="button" className={className} aria-pressed={paid} aria-label={label} title={title}
            disabled={busy} onClick={onToggle}>
      <Check />
    </button>
  )
}
