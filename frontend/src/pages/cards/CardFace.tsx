import { cardColorClass, maskedNumber } from '../../cards'
import type { CardBrand, CardColor } from '../../types'
import { BrandMark } from './BrandMark'

// O que o desenho do cartão mostra. Texto vazio = ainda não informado: assim o
// mesmo componente serve para um cartão salvo e para a prévia do formulário,
// que vai se preenchendo enquanto a pessoa digita.
export interface CardFaceData {
  name: string
  brand: CardBrand | null
  holderName: string
  lastFour: string // de 0 a 4 dígitos
  expiry: string // "11/29"
  color: CardColor | null // null = cartão antigo, sem os dados: desenho neutro
}

// O desenho de um cartão de crédito: bandeira e nome em cima; número (só o
// final), titular e validade embaixo. Não tem clique nem estado: quem usa
// decide o que fazer com ele (um botão na pilha, a prévia no painel).
//
// Tudo <span>: o desenho vai dentro de um <button> na pilha, e botão só pode
// conter elementos "de texto". O layout de bloco vem do CSS (.paycard).
export function CardFace({ card }: { card: CardFaceData }) {
  const colorClass = card.color === null ? 'blank' : cardColorClass(card.color)

  return (
    <span className={`paycard ${colorClass}`}>
      <span className="paycard-top">
        <BrandMark brand={card.brand} />
        <span className="paycard-name">{card.name}</span>
      </span>
      <span className="paycard-bottom">
        <span className="paycard-number">{maskedNumber(card.lastFour)}</span>
        {card.color === null ? (
          <span className="paycard-holder">Faltam dados: toque para completar</span>
        ) : (
          <span className="paycard-row">
            <span className="paycard-holder">{card.holderName || 'Nome do titular'}</span>
            <span className="paycard-expiry">{card.expiry || 'MM/AA'}</span>
          </span>
        )}
      </span>
    </span>
  )
}
