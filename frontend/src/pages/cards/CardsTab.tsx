import { useState, type CSSProperties } from 'react'
import { CARD_COLORS, describeCard, formatExpiry, isComplete, stackPositions } from '../../cards'
import { CreditCard } from '../../icons'
import type { CardResponse } from '../../types'
import { CardFace, type CardFaceData } from './CardFace'
import { CardSheet } from './CardSheet'

// Painel fechado, aberto para cadastrar, ou aberto editando um cartão.
type SheetState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; card: CardResponse }

// Do cartão salvo para o que o desenho mostra. No cartão antigo (só com o
// nome) os dados vêm null e viram texto vazio.
function faceOf(card: CardResponse): CardFaceData {
  return {
    name: card.name,
    brand: card.brand,
    holderName: card.holderName ?? '',
    lastFour: card.lastFour ?? '',
    expiry: card.expiryMonth && card.expiryYear ? formatExpiry(card.expiryMonth, card.expiryYear) : '',
    color: card.color,
  }
}

// Aba Cartões: os cartões empilhados como numa carteira. De cada cartão de
// trás aparece só a faixa de cima (bandeira e nome); o da frente aparece
// inteiro. Tocar num cartão de trás traz ele para a frente; tocar no da
// frente abre a edição.
//
// A lista de cartões é da página (CategoriesPage), que também a usa para saber
// se já carregou. Aqui fica só o estado da tela: quem está na frente e o painel.
export function CardsTab({ cards, onSaved, onDeleted }: {
  cards: CardResponse[] // em ordem alfabética
  onSaved: (card: CardResponse) => void // criado ou editado
  onDeleted: (id: number) => void
}) {
  // Cartão trazido para a frente. null = ninguém escolheu: o último da lista.
  const [frontId, setFrontId] = useState<number | null>(null)
  const [sheet, setSheet] = useState<SheetState>({ mode: 'closed' })

  const positions = stackPositions(cards.map(c => c.id), frontId)
  const frontPosition = cards.length - 1
  // Na página, os <li> ficam sempre na ordem de criação (id); o LUGAR de cada
  // um na pilha vem só do CSS (--pos). Se os <li> seguissem a ordem alfabética,
  // renomear um cartão faria o React mover elementos de lugar, e elemento
  // movido recomeça a animação de entrada.
  const byId = [...cards].sort((a, b) => a.id - b.id)

  function handleSaved(saved: CardResponse) {
    onSaved(saved)
    // O cartão novo (ou recém-editado) fica na frente: a pessoa vê o resultado.
    setFrontId(saved.id)
    setSheet({ mode: 'closed' })
  }

  function handleDeleted(id: number) {
    onDeleted(id)
    setSheet({ mode: 'closed' })
  }

  return (
    <>
      {cards.length === 0 ? (
        <div className="empty reveal">
          <CreditCard />
          <p>Nenhum cartão cadastrado ainda.</p>
        </div>
      ) : (
        <div className="wallet-area reveal">
          {/* --count e --pos alimentam as contas do CSS (.wallet): quantas faixas
              a pilha tem e em que altura fica cada cartão. */}
          <ul className="wallet" style={{ '--count': cards.length } as CSSProperties}>
            {byId.map(card => {
              const position = positions.get(card.id)!
              const isFront = position === frontPosition
              const action = isFront
                ? isComplete(card) ? 'Editar o cartão' : 'Completar os dados do cartão'
                : 'Trazer para a frente o cartão'
              return (
                <li key={card.id} className="wallet-item" style={{ '--pos': position } as CSSProperties}>
                  <button type="button" className="wallet-card" aria-label={`${action} ${describeCard(card)}`}
                          onClick={() => (isFront ? setSheet({ mode: 'edit', card }) : setFrontId(card.id))}>
                    <CardFace card={faceOf(card)} />
                  </button>
                </li>
              )
            })}
          </ul>
          <p className="hint wallet-hint">
            {cards.length === 1
              ? 'Toque no cartão para editar.'
              : 'Toque num cartão para trazê-lo para a frente. Toque de novo para editar.'}
          </p>
        </div>
      )}

      <button type="button" className="btn-primary wallet-add" onClick={() => setSheet({ mode: 'create' })}>
        Adicionar cartão
      </button>
      <p className="muted wallet-note">
        O cartão é escolhido nas compras no crédito. A categoria continua dizendo o tipo do gasto.
      </p>

      {sheet.mode !== 'closed' && (
        <CardSheet
          card={sheet.mode === 'edit' ? sheet.card : undefined}
          // Cada cartão novo já vem com a cor seguinte: três cartões, três cores,
          // mesmo que ninguém mexa no seletor.
          defaultColor={CARD_COLORS[cards.length % CARD_COLORS.length]}
          onClose={() => setSheet({ mode: 'closed' })}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
        />
      )}
    </>
  )
}
