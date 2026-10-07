import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from './api'
import { formatPaymentMethod, parseAmount, PAYMENT_METHODS, todayIso } from './format'
import { Close } from './icons'
import type {
  CategoryResponse, PaymentMethod, TransactionRequest, TransactionResponse, TransactionType,
} from './types'

// Painel que sobe por baixo com o formulário de nova transação.
// Usa o <dialog> nativo: showModal() já prende o foco, fecha com Esc,
// escurece o fundo e deixa a página de trás inacessível.
// O pai só renderiza este componente enquanto o painel está aberto,
// então todo o estado do formulário começa zerado a cada abertura.
export function TransactionSheet({ onClose, onSaved }: {
  onClose: () => void
  onSaved: (tx: TransactionResponse) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  const [type, setType] = useState<TransactionType>('EXPENSE')
  const [amountText, setAmountText] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null)
  const [categoryId, setCategoryId] = useState('') // '' = sem categoria
  const [description, setDescription] = useState('')
  const [occurredOn, setOccurredOn] = useState(todayIso)
  const [categories, setCategories] = useState<CategoryResponse[]>([])

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

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
  }, [])

  // Receita não tem forma de pagamento e as categorias são de outro tipo:
  // ao trocar o tipo, limpamos os dois para não enviar algo que o backend recusaria.
  function changeType(next: TransactionType) {
    setType(next)
    setPaymentMethod(null)
    setCategoryId('')
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
    if (occurredOn === '') {
      localErrors.occurredOn = 'Informe a data'
    }
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors)
      return
    }

    const request: TransactionRequest = {
      amount: amount!,
      type,
      paymentMethod: type === 'EXPENSE' ? paymentMethod : null,
      categoryId: categoryId === '' ? null : Number(categoryId),
      description: description.trim() === '' ? null : description.trim(),
      occurredOn,
    }

    setSaving(true)
    try {
      const saved = await api<TransactionResponse>('/api/transactions', {
        method: 'POST',
        body: JSON.stringify(request),
      })
      onSaved(saved)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Não foi possível conectar ao servidor')
      }
      setSaving(false)
    }
  }

  const categoriesOfType = categories.filter(c => c.type === type)

  return (
    <dialog
      ref={dialogRef}
      className="sheet"
      aria-labelledby="sheet-title"
      // Esc ou dialog.close(): avisa o pai, que desmonta o componente.
      onClose={onClose}
      // Clique no fundo escurecido: o alvo é o próprio <dialog> (o conteúdo
      // fica dentro de .sheet-body, que ocupa todo o painel).
      onClick={e => {
        if (e.target === e.currentTarget) dialogRef.current?.close()
      }}
    >
      <div className="sheet-body">
        <div className="sheet-handle" aria-hidden="true" />
        <header className="sheet-header">
          <h2 id="sheet-title">Nova transação</h2>
          <button type="button" className="icon-btn" aria-label="Fechar"
                  onClick={() => dialogRef.current?.close()}>
            <Close />
          </button>
        </header>

        <form onSubmit={handleSubmit} noValidate>
          <div className="segmented" role="radiogroup" aria-label="Tipo">
            {(['EXPENSE', 'INCOME'] as const).map(t => (
              <button key={t} type="button" role="radio" aria-checked={type === t}
                      className={type === t ? 'active' : ''} onClick={() => changeType(t)}>
                {t === 'EXPENSE' ? 'Despesa' : 'Receita'}
              </button>
            ))}
          </div>

          <label className="amount-field">
            Valor
            <div className="amount-input">
              <span>R$</span>
              {/* inputMode="decimal": no celular abre o teclado numérico com vírgula. */}
              <input inputMode="decimal" placeholder="0,00" value={amountText}
                     onChange={e => setAmountText(e.target.value)} autoFocus />
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
                          onClick={() => setPaymentMethod(m)}>
                    {formatPaymentMethod(m)}
                  </button>
                ))}
              </div>
              {fieldErrors.paymentMethod && <p className="field-error">{fieldErrors.paymentMethod}</p>}
            </div>
          )}

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
          </label>
          {fieldErrors.description && <p className="field-error">{fieldErrors.description}</p>}

          <label>
            Data
            <input type="date" value={occurredOn} onChange={e => setOccurredOn(e.target.value)} required />
          </label>
          {fieldErrors.occurredOn && <p className="field-error">{fieldErrors.occurredOn}</p>}

          {error && <p className="error">{error}</p>}
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Salvando...' : 'Salvar'}
          </button>
        </form>
      </div>
    </dialog>
  )
}
