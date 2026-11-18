// Funções puras da aba Cartões (testadas em cards.test.ts): rótulos, o texto
// que aparece no desenho do cartão, a validade digitada e a ordem da pilha.

import type { CardBrand, CardColor, CardResponse } from './types'

const BRAND_LABELS: Record<CardBrand, string> = {
  VISA: 'Visa',
  MASTERCARD: 'Mastercard',
  ELO: 'Elo',
  AMEX: 'Amex',
  HIPERCARD: 'Hipercard',
  OUTRA: 'Outra',
}

const COLOR_LABELS: Record<CardColor, string> = {
  AURORA: 'Degradê',
  ROXO: 'Roxo',
  PRETO: 'Preto',
}

// Na ordem em que aparecem no formulário.
export const CARD_BRANDS = Object.keys(BRAND_LABELS) as CardBrand[]
export const CARD_COLORS = Object.keys(COLOR_LABELS) as CardColor[]

export function formatBrand(brand: CardBrand): string {
  return BRAND_LABELS[brand]
}

export function formatCardColor(color: CardColor): string {
  return COLOR_LABELS[color]
}

// Classe do index.css que pinta cada cor (.paycard.aurora, .swatch.roxo...).
const COLOR_CLASSES: Record<CardColor, string> = {
  AURORA: 'aurora',
  ROXO: 'roxo',
  PRETO: 'preto',
}

export function cardColorClass(color: CardColor): string {
  return COLOR_CLASSES[color]
}

// Cartão cadastrado antes de existirem bandeira, titular, final, validade e
// cor só tem o nome. O backend garante "todos ou nenhum", então olhar um
// campo basta.
export function isComplete(card: CardResponse): boolean {
  return card.brand !== null
}

// O número como aparece no cartão: só os 4 últimos dígitos são conhecidos.
// Aceita o campo pela metade (prévia enquanto a pessoa digita):
// "32" -> "•••• •••• •••• 32••".
export function maskedNumber(lastFour: string): string {
  return `•••• •••• •••• ${lastFour.padEnd(4, '•')}`
}

// Campo "últimos 4 dígitos": só dígitos, no máximo 4.
// Digitar um 5º dígito não muda nada. Colar um número inteiro (o do cartão)
// deixa só os 4 últimos: o resto nem chega a ficar no campo.
export function lastFourInput(previous: string, typed: string): string {
  const digits = typed.replace(/\D/g, '')
  if (digits.length <= 4) {
    return digits
  }
  // Vários caracteres de uma vez só acontece ao colar.
  const pasted = digits.length - previous.length > 1
  return pasted ? digits.slice(-4) : previous
}

// formatExpiry(3, 2029) -> "03/29", como está impresso no cartão.
export function formatExpiry(month: number, year: number): string {
  return `${String(month).padStart(2, '0')}/${String(year % 100).padStart(2, '0')}`
}

// Arruma a validade enquanto a pessoa digita: só dígitos, no máximo 4, e a
// barra entra sozinha. "1129" -> "11/29".
export function formatExpiryInput(text: string): string {
  // "3/" -> "03/": quem digita a barra depois de um dígito só quis dizer março.
  const padded = text.replace(/^(\d)\//, '0$1/')
  let digits = padded.replace(/\D/g, '')
  // Nenhum mês começa com 2 a 9: "5" só pode ser maio, então vira "05".
  // Vale também para o que é colado de uma vez: "429" -> "04/29".
  if (digits !== '' && digits[0] > '1') {
    digits = `0${digits}`
  }
  digits = digits.slice(0, 4)
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits
}

// "11/29" -> { month: 11, year: 2029 }, ou null se não for uma validade.
// O cartão mostra o ano com dois dígitos: "29" é sempre 2029.
export function parseExpiry(text: string): { month: number; year: number } | null {
  const match = /^(\d{2})\/(\d{2})$/.exec(text.trim())
  if (!match) {
    return null
  }
  const month = Number(match[1])
  if (month < 1 || month > 12) {
    return null
  }
  return { month, year: 2000 + Number(match[2]) }
}

// Lugar de cada cartão na pilha: 0 é o de trás (lá em cima, só a faixa
// aparecendo) e o maior número é o da frente (inteiro à mostra).
// O cartão escolhido vai para a frente e os outros mantêm a ordem entre si.
// Sem escolha (ou se o escolhido não existe mais), o último da lista fica na frente.
export function stackPositions(ids: readonly number[], frontId: number | null): Map<number, number> {
  const front = frontId !== null && ids.includes(frontId) ? frontId : ids.at(-1)
  const positions = new Map<number, number>()
  for (const id of ids) {
    if (id !== front) {
      positions.set(id, positions.size)
    }
  }
  if (front !== undefined) {
    positions.set(front, positions.size)
  }
  return positions
}

// Descrição do cartão para o leitor de tela: "Nubank, Mastercard, final 3210".
export function describeCard(card: CardResponse): string {
  if (card.brand === null || card.lastFour === null) {
    return `${card.name}, dados incompletos`
  }
  return `${card.name}, ${formatBrand(card.brand)}, final ${card.lastFour}`
}
