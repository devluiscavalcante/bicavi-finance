import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from './api'
import { ConfirmDialog } from './ConfirmDialog'
import { formatPaymentMethod, installmentPreview, parseAmount, PAYMENT_METHODS } from './format'
import { Close } from './icons'
import type {
  CardResponse, CategoryResponse, EditScope, InstallmentRequest, PaymentMethod, PeriodResponse, TransactionRequest,
  TransactionResponse, TransactionType,
} from './types'

// 1x (à vista) até 24x, o mesmo limite do backend (@Min(2) @Max(24) no parcelado).
const INSTALLMENT_OPTIONS = Array.from({ length: 24 }, (_, i) => i + 1)

// Painel que sobe por baixo com o formulário de transação: cria uma nova ou,
// se receber `transaction`, edita/exclui a existente.
// Usa o <dialog> nativo: showModal() já prende o foco, fecha com Esc,
// escurece o fundo e deixa a página de trás inacessível.
// O pai só renderiza este componente enquanto o painel está aberto,
// então todo o estado do formulário começa do zero a cada abertura.
export function TransactionSheet({ transaction, defaultDate, period, onClose, onSaved, onDeleted }: {
  transaction?: TransactionResponse // ausente = nova transação
  defaultDate: string // data inicial de uma transação nova
  period: PeriodResponse
  onClose: () => void
  onSaved: (tx: TransactionResponse) => void
  onDeleted: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const editing = transaction !== undefined
  // Parcela de uma compra: sempre despesa, e a edição pode valer para as seguintes.
  const installmentNumber = transaction?.installmentNumber ?? null
  const installmentCount = transaction?.installmentCount ?? null
  const isInstallment = installmentNumber !== null && installmentCount !== null
  const followingCount = isInstallment ? installmentCount - installmentNumber : 0

  const [type, setType] = useState<TransactionType>(transaction?.type ?? 'EXPENSE')
  // 30.5 -> "30,5": o mesmo formato que a pessoa digitaria.
  const [amountText, setAmountText] = useState(transaction ? String(transaction.amount).replace('.', ',') : '')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(transaction?.paymentMethod ?? null)
  // '' = nenhum. Uma despesa antiga no crédito pode chegar sem cartão: o campo
  // aparece vazio e o salvar pede para escolher.
  const [cardId, setCardId] = useState(transaction?.cardId?.toString() ?? '')
  const [categoryId, setCategoryId] = useState(transaction?.categoryId?.toString() ?? '') // '' = sem categoria
  const [description, setDescription] = useState(transaction?.description ?? '')
  const [occurredOn, setOccurredOn] = useState(transaction?.occurredOn ?? defaultDate)
  // 1 = à vista. Só existe na criação de despesa.
  const [installments, setInstallments] = useState(1)
  // Editando parcela: "só esta" ou "esta e as próximas" (vale para salvar e excluir).
  const [scope, setScope] = useState<EditScope>('THIS')
  const [categories, setCategories] = useState<CategoryResponse[]>([])
  // null = ainda carregando (não mostra "nenhum cartão" antes da resposta chegar).
  const [cards, setCards] = useState<CardResponse[] | null>(null)

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const canSplit = !editing && type === 'EXPENSE'
  const split = canSplit && installments > 1
  const previewAmount = parseAmount(amountText)
  const applyToFollowing = isInstallment && scope === 'FOLLOWING'
  const isCredit = type === 'EXPENSE' && paymentMethod === 'CREDITO'

  useEffect(() => {
    const dialog = dialogRef.current
    // O StrictMode do React roda efeitos duas vezes em dev; showModal() num
    // dialog já aberto lançaria erro, daí a checagem.
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [])

  useEffect(() => {
    api<CategoryResponse[]>('/api/categories').then(setCategories).catch(() => {
      // Sem categorias o formulário ainda funciona ("Sem categoria").
    })
    api<CardResponse[]>('/api/cards')
      .then(list => setCards([...list].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))))
      .catch(() => setCards([]))
  }, [])

  // Cartão só existe no crédito: trocar a forma de pagamento limpa a escolha.
  // Com um único cartão cadastrado, o crédito já vem com ele selecionado.
  function changePaymentMethod(next: PaymentMethod) {
    setPaymentMethod(next)
    if (next !== 'CREDITO') {
      setCardId('')
    } else if (cardId === '' && cards?.length === 1) {
      setCardId(String(cards[0].id))
    }
  }

  // Receita não tem forma de pagamento, nem parcelas, e as categorias são de
  // outro tipo: ao trocar o tipo, limpamos tudo isso para não enviar algo que o
  // backend recusaria.
  function changeType(next: TransactionType) {
    setType(next)
    setPaymentMethod(null)
    setCardId('')
    setCategoryId('')
    setInstallments(1)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    // Validação local: só para avisar antes. O backend valida tudo de novo.
    const amount = parseAmount(amountText)
    const localErrors: Record<string, string> = {}
    if (amount === null) {
      localErrors.amount = 'Informe um valor válido, ex.: 30,50'
    }
    if (type === 'EXPENSE' && paymentMethod === null) {
      localErrors.paymentMethod = 'Escolha a forma de pagamento'
    }
    if (isCredit && cardId === '') {
      localErrors.cardId = 'Escolha o cartão da compra'
    }
    if (occurredOn === '') {
      localErrors.occurredOn = 'Informe a data'
    }
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors)
      return
    }

    const categoryValue = categoryId === '' ? null : Number(categoryId)
    const cardValue = isCredit ? Number(cardId) : null
    const descriptionValue = description.trim() === '' ? null : description.trim()

    setSaving(true)
    try {
      let saved: TransactionResponse
      if (split) {
        const request: InstallmentRequest = {
          totalAmount: amount!,
          installments,
          paymentMethod,
          cardId: cardValue,
          categoryId: categoryValue,
          description: descriptionValue,
          firstDate: occurredOn,
        }
        const parts = await api<TransactionResponse[]>('/api/transactions/installments', {
          method: 'POST',
          body: JSON.stringify(request),
        })
        // A tela do mês vai para o mês da 1ª parcela e a destaca.
        saved = parts[0]
      } else {
        const request: TransactionRequest = {
          amount: amount!,
          type,
          paymentMethod: type === 'EXPENSE' ? paymentMethod : null,
          cardId: cardValue,
          categoryId: categoryValue,
          description: descriptionValue,
          occurredOn,
        }
        // PUT substitui todos os campos (mesmo TransactionRequest do POST).
        saved = await api<TransactionResponse>(
          editing ? `/api/transactions/${transaction.id}?scope=${scope}` : '/api/transactions',
          { method: editing ? 'PUT' : 'POST', body: JSON.stringify(request) },
        )
      }
      onSaved(saved)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        // O parcelado chama o valor de "totalAmount" e a data de "firstDate":
        // mostramos os erros nos mesmos campos da tela.
        const { totalAmount, firstDate, ...others } = e.fieldErrors
        setFieldErrors({
          ...others,
          ...(totalAmount && { amount: totalAmount }),
          ...(firstDate && { occurredOn: firstDate }),
        })
      } else {
        setError('Não foi possível conectar ao servidor')
      }
      setSaving(false)
    }
  }

  async function handleDelete() {
    // Erro aqui é exibido pelo próprio ConfirmDialog.
    await api<void>(`/api/transactions/${transaction!.id}?scope=${scope}`, { method: 'DELETE' })
    onDeleted()
  }

  const categoriesOfType = categories.filter(c => c.type === type)

  return (
    <dialog
      ref={dialogRef}
      className="sheet"
      aria-labelledby="sheet-title"
      // O evento "close" do ConfirmDialog (que fica dentro deste componente)
      // também chega aqui pela árvore do React: sem o teste do alvo, cancelar
      // a exclusão fecharia o painel inteiro.
      onClose={e => {
        if (e.target === e.currentTarget) onClose()
      }}
      // Clique no fundo escurecido: o alvo é o próprio <dialog> (o conteúdo
      // fica dentro de .sheet-body, que ocupa todo o painel).
      onClick={e => {
        if (e.target === e.currentTarget) dialogRef.current?.close()
      }}
    >
      <div className="sheet-body">
        <div className="sheet-handle" aria-hidden="true" />
        <header className="sheet-header">
          <h2 id="sheet-title">
            {isInstallment
              ? `Parcela ${installmentNumber} de ${installmentCount}`
              : editing ? 'Editar transação' : 'Nova transação'}
          </h2>
          <button type="button" className="icon-btn" aria-label="Fechar"
                  onClick={() => dialogRef.current?.close()}>
            <Close />
          </button>
        </header>

        <form onSubmit={handleSubmit} noValidate>
          {/* Parcela é sempre despesa: não oferece trocar o tipo. */}
          {!isInstallment && (
            <div className="segmented" role="radiogroup" aria-label="Tipo">
              {(['EXPENSE', 'INCOME'] as const).map(t => (
                <button key={t} type="button" role="radio" aria-checked={type === t}
                        className={type === t ? 'active' : ''} onClick={() => changeType(t)}>
                  {t === 'EXPENSE' ? 'Despesa' : 'Receita'}
                </button>
              ))}
            </div>
          )}

          {followingCount > 0 && (
            <div className="field">
              <span className="field-label">Aplicar alterações em</span>
              <div className="segmented" role="radiogroup" aria-label="Aplicar alterações em">
                <button type="button" role="radio" aria-checked={scope === 'THIS'}
                        className={scope === 'THIS' ? 'active' : ''} onClick={() => setScope('THIS')}>
                  Só esta parcela
                </button>
                <button type="button" role="radio" aria-checked={scope === 'FOLLOWING'}
                        className={scope === 'FOLLOWING' ? 'active' : ''} onClick={() => setScope('FOLLOWING')}>
                  Esta e as próximas
                </button>
              </div>
              {applyToFollowing && (
                <span className="hint">
                  Categoria, forma de pagamento, cartão e descrição valem também para as {followingCount} parcela(s)
                  seguinte(s). Valor e data mudam só nesta.
                </span>
              )}
            </div>
          )}

          <label className="amount-field">
            {split ? 'Valor total da compra' : 'Valor'}
            <div className="amount-input">
              <span>R$</span>
              {/* inputMode="decimal": no celular abre o teclado numérico com vírgula. */}
              <input inputMode="decimal" placeholder="0,00" value={amountText}
                     onChange={e => setAmountText(e.target.value)} autoFocus={!editing} />
            </div>
          </label>
          {fieldErrors.amount && <p className="field-error">{fieldErrors.amount}</p>}

          {type === 'EXPENSE' && (
            <div className="field">
              <span className="field-label">Forma de pagamento</span>
              <div className="chips" role="radiogroup" aria-label="Forma de pagamento">
                {PAYMENT_METHODS.map(m => (
                  <button key={m} type="button" role="radio" aria-checked={paymentMethod === m}
                          className={paymentMethod === m ? 'chip active' : 'chip'}
                          onClick={() => changePaymentMethod(m)}>
                    {formatPaymentMethod(m)}
                  </button>
                ))}
              </div>
              {fieldErrors.paymentMethod && <p className="field-error">{fieldErrors.paymentMethod}</p>}
            </div>
          )}

          {isCredit && cards !== null && (
            cards.length === 0 ? (
              <p className="hint">
                Nenhum cartão cadastrado.{' '}
                <Link to="/categorias?aba=cartoes">Cadastre seus cartões</Link> para lançar compras no crédito.
              </p>
            ) : (
              <label>
                Cartão
                <select value={cardId} onChange={e => setCardId(e.target.value)}>
                  <option value="" disabled>Escolha o cartão</option>
                  {cards.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </label>
            )
          )}
          {fieldErrors.cardId && <p className="field-error">{fieldErrors.cardId}</p>}

          {canSplit && (
            <label>
              Parcelas
              <select value={installments} onChange={e => setInstallments(Number(e.target.value))}>
                {INSTALLMENT_OPTIONS.map(n => (
                  <option key={n} value={n}>{n === 1 ? 'À vista' : `${n}x`}</option>
                ))}
              </select>
              {split && previewAmount !== null && (
                <span className="hint">{installmentPreview(previewAmount, installments)}</span>
              )}
            </label>
          )}
          {fieldErrors.installments && <p className="field-error">{fieldErrors.installments}</p>}

          <label>
            Categoria
            <select value={categoryId} onChange={e => setCategoryId(e.target.value)}>
              <option value="">Sem categoria</option>
              {categoriesOfType.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </label>

          <label>
            Descrição
            <input value={description} onChange={e => setDescription(e.target.value)}
                   placeholder={type === 'EXPENSE' ? 'Ex.: Mercado' : 'Ex.: Salário'} maxLength={255} />
            {split && <span className="hint">Todas as parcelas recebem esta descrição, com “(1/{installments})”, “(2/{installments})”…</span>}
          </label>
          {fieldErrors.description && <p className="field-error">{fieldErrors.description}</p>}

          <label>
            {split ? 'Data da 1ª parcela' : 'Data'}
            {/* min: o seletor de data não oferece meses fechados. O backend recusa de qualquer jeito. */}
            <input type="date" value={occurredOn} min={`${period.firstEditableMonth}-01`}
                   onChange={e => setOccurredOn(e.target.value)} required />
            {/* Combinado com o casal: no crédito, a compra entra no mês em que a fatura é PAGA. */}
            {paymentMethod === 'CREDITO' && type === 'EXPENSE' && (
              <span className="hint">
                No crédito, use a data de vencimento da fatura em que {split ? 'a 1ª parcela' : 'a compra'} cai.
              </span>
            )}
          </label>
          {fieldErrors.occurredOn && <p className="field-error">{fieldErrors.occurredOn}</p>}

          {error && <p className="error">{error}</p>}
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving
              ? 'Salvando...'
              : split ? `Lançar ${installments} parcelas`
                : applyToFollowing ? `Salvar nesta e nas próximas ${followingCount}`
                  : editing ? 'Salvar alterações' : 'Salvar'}
          </button>
          {editing && (
            <button type="button" className="btn-text-danger" disabled={saving}
                    onClick={() => setConfirmingDelete(true)}>
              {applyToFollowing ? 'Excluir esta e as próximas parcelas' : isInstallment ? 'Excluir esta parcela' : 'Excluir transação'}
            </button>
          )}
        </form>
      </div>

      {confirmingDelete && (
        <ConfirmDialog
          title={applyToFollowing ? `Excluir ${followingCount + 1} parcelas?` : 'Excluir transação?'}
          message={applyToFollowing
            ? `Esta parcela e as ${followingCount} seguinte(s) saem das listas e dos totais. As anteriores continuam. Esta ação não pode ser desfeita.`
            : 'Ela sai da lista e dos totais do mês. Esta ação não pode ser desfeita.'}
          confirmLabel="Excluir"
          onConfirm={handleDelete}
          onCancel={() => setConfirmingDelete(false)}
        />
      )}
    </dialog>
  )
}
