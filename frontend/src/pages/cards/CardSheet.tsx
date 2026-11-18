import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../../api'
import {
  CARD_BRANDS, CARD_COLORS, cardColorClass, formatBrand, formatCardColor, formatExpiry, formatExpiryInput,
  isComplete, lastFourInput, parseExpiry,
} from '../../cards'
import { ConfirmDialog } from '../../ConfirmDialog'
import { Close } from '../../icons'
import type { CardBrand, CardColor, CardRequest, CardResponse } from '../../types'
import { CardFace } from './CardFace'

// Painel que sobe por baixo com o formulário do cartão: cadastra um novo ou,
// se receber `card`, edita/exclui o existente. No topo, o desenho do cartão
// vai se preenchendo enquanto a pessoa digita.
//
// Mesmo padrão do TransactionSheet: <dialog> nativo, e o pai só renderiza este
// componente enquanto o painel está aberto (o formulário começa do zero a cada
// abertura).
export function CardSheet({ card, defaultColor, onClose, onSaved, onDeleted }: {
  card?: CardResponse // ausente = cartão novo
  defaultColor: CardColor // cor que já vem marcada num cartão novo
  onClose: () => void
  onSaved: (card: CardResponse) => void
  onDeleted: (id: number) => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const editing = card !== undefined
  // Cartão antigo, que só tem o nome: o painel serve para completar o resto.
  const completing = editing && !isComplete(card)

  const [name, setName] = useState(card?.name ?? '')
  const [brand, setBrand] = useState<CardBrand | null>(card?.brand ?? null)
  const [holderName, setHolderName] = useState(card?.holderName ?? '')
  const [lastFour, setLastFour] = useState(card?.lastFour ?? '')
  // Um campo só na tela ("11/29"); a API recebe mês e ano separados.
  const [expiry, setExpiry] = useState(
    card?.expiryMonth && card.expiryYear ? formatExpiry(card.expiryMonth, card.expiryYear) : '',
  )
  const [color, setColor] = useState<CardColor>(card?.color ?? defaultColor)

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    // O StrictMode do React roda efeitos duas vezes em dev; showModal() num
    // dialog já aberto lançaria erro, daí a checagem.
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [])

  // Mexer num campo apaga o aviso dele; os dos outros campos continuam até
  // salvar de novo. Sem isso, um formulário já corrigido seguia cheio de avisos.
  function clearError(field: string) {
    setFieldErrors(errors => {
      if (!(field in errors)) return errors
      const rest = { ...errors }
      delete rest[field]
      return rest
    })
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    // Validação local: só para avisar antes. O backend valida tudo de novo.
    const parsedExpiry = parseExpiry(expiry)
    const localErrors: Record<string, string> = {}
    if (name.trim() === '') {
      localErrors.name = 'Informe o nome do cartão'
    }
    if (brand === null) {
      localErrors.brand = 'Escolha a bandeira'
    }
    if (holderName.trim() === '') {
      localErrors.holderName = 'Informe o nome do titular'
    }
    if (lastFour.length !== 4) {
      localErrors.lastFour = 'Informe os 4 últimos dígitos'
    }
    if (parsedExpiry === null) {
      localErrors.expiry = 'Informe a validade como MM/AA, ex.: 11/29'
    }
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors)
      return
    }

    const request: CardRequest = {
      name: name.trim(),
      brand: brand!,
      holderName: holderName.trim(),
      lastFour,
      expiryMonth: parsedExpiry!.month,
      expiryYear: parsedExpiry!.year,
      color,
    }

    setSaving(true)
    try {
      // PUT substitui o cartão inteiro (o mesmo CardRequest do POST).
      const saved = await api<CardResponse>(editing ? `/api/cards/${card.id}` : '/api/cards', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify(request),
      })
      onSaved(saved)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        // A API chama a validade de "expiryMonth" e "expiryYear": na tela os
        // dois são um campo só.
        const { expiryMonth, expiryYear, ...others } = e.fieldErrors
        const expiryError = expiryMonth ?? expiryYear
        setFieldErrors({ ...others, ...(expiryError && { expiry: expiryError }) })
      } else {
        setError('Não foi possível conectar ao servidor')
      }
      setSaving(false)
    }
  }

  async function handleDelete() {
    // Erro aqui é exibido pelo próprio ConfirmDialog.
    await api<void>(`/api/cards/${card!.id}`, { method: 'DELETE' })
    onDeleted(card!.id)
  }

  return (
    <dialog
      ref={dialogRef}
      className="sheet"
      aria-labelledby="card-sheet-title"
      // O "close" do ConfirmDialog (que fica dentro deste componente) também
      // chega aqui pela árvore do React: sem o teste do alvo, cancelar a
      // exclusão fecharia o painel inteiro.
      onClose={e => {
        if (e.target === e.currentTarget) onClose()
      }}
      // Clique no fundo escurecido: o alvo é o próprio <dialog>.
      onClick={e => {
        if (e.target === e.currentTarget) dialogRef.current?.close()
      }}
    >
      <div className="sheet-body">
        <div className="sheet-handle" aria-hidden="true" />
        <header className="sheet-header">
          <h2 id="card-sheet-title">
            {completing ? 'Completar cartão' : editing ? 'Editar cartão' : 'Novo cartão'}
          </h2>
          <button type="button" className="icon-btn" aria-label="Fechar"
                  onClick={() => dialogRef.current?.close()}>
            <Close />
          </button>
        </header>

        {/* Prévia: escondida do leitor de tela, que já lê os mesmos dados nos campos. */}
        <div className="paycard-preview" aria-hidden="true">
          <CardFace card={{ name: name.trim() || 'Nome do cartão', brand, holderName: holderName.trim(), lastFour, expiry, color }} />
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <label>
            Nome do cartão
            <input value={name} onChange={e => { setName(e.target.value); clearError('name') }} maxLength={50}
                   placeholder="Ex.: Nubank" autoComplete="off" autoFocus={!editing} />
            <span className="hint">É como ele aparece nas compras e nas faturas.</span>
          </label>
          {fieldErrors.name && <p className="field-error">{fieldErrors.name}</p>}

          <div className="field">
            <span className="field-label">Bandeira</span>
            <div className="chips" role="radiogroup" aria-label="Bandeira">
              {CARD_BRANDS.map(b => (
                <button key={b} type="button" role="radio" aria-checked={brand === b}
                        className={brand === b ? 'chip active' : 'chip'}
                        onClick={() => { setBrand(b); clearError('brand') }}>
                  {formatBrand(b)}
                </button>
              ))}
            </div>
            {fieldErrors.brand && <p className="field-error">{fieldErrors.brand}</p>}
          </div>

          <label>
            Nome do titular
            <input value={holderName} onChange={e => { setHolderName(e.target.value); clearError('holderName') }}
                   maxLength={40}
                   placeholder="Como está no cartão" autoComplete="off" autoCapitalize="words" />
          </label>
          {fieldErrors.holderName && <p className="field-error">{fieldErrors.holderName}</p>}

          <div className="field">
            <div className="field-row">
              <label>
                Últimos 4 dígitos
                {/* inputMode="numeric": no celular abre o teclado só de números.
                    autoComplete="off": o navegador não oferece um cartão salvo aqui. */}
                <input value={lastFour} inputMode="numeric" placeholder="0000" autoComplete="off"
                       onChange={e => { setLastFour(lastFourInput(lastFour, e.target.value)); clearError('lastFour') }} />
              </label>
              <label>
                Validade
                <input value={expiry} inputMode="numeric" placeholder="MM/AA" autoComplete="off"
                       onChange={e => { setExpiry(formatExpiryInput(e.target.value)); clearError('expiry') }} />
              </label>
            </div>
            {fieldErrors.lastFour && <p className="field-error">{fieldErrors.lastFour}</p>}
            {fieldErrors.expiry && <p className="field-error">{fieldErrors.expiry}</p>}
            <span className="hint">
              Só o final do cartão, para reconhecê-lo. O número completo e o código de segurança
              nunca são pedidos.
            </span>
          </div>

          <div className="field">
            <span className="field-label">Cor do cartão</span>
            <div className="chips" role="radiogroup" aria-label="Cor do cartão">
              {CARD_COLORS.map(c => (
                <button key={c} type="button" role="radio" aria-checked={color === c}
                        className={color === c ? 'chip active' : 'chip'} onClick={() => setColor(c)}>
                  <span className={`swatch ${cardColorClass(c)}`} aria-hidden="true" />
                  {formatCardColor(c)}
                </button>
              ))}
            </div>
          </div>

          {error && <p className="error">{error}</p>}
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Salvando...' : editing ? 'Salvar alterações' : 'Adicionar cartão'}
          </button>
          {editing && (
            <button type="button" className="btn-text-danger" disabled={saving}
                    onClick={() => setConfirmingDelete(true)}>
              Excluir cartão
            </button>
          )}
        </form>
      </div>

      {confirmingDelete && (
        <ConfirmDialog
          title={`Excluir "${card!.name}"?`}
          message={'As compras deste cartão não serão apagadas: elas ficarão sem cartão. '
            + 'Cartão usado em mês fechado não pode ser excluído.'}
          confirmLabel="Excluir"
          onConfirm={handleDelete}
          onCancel={() => setConfirmingDelete(false)}
        />
      )}
    </dialog>
  )
}
