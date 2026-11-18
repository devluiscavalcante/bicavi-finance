import type { CardBrand } from '../../types'

// Marca da bandeira no canto do cartão, desenhada em SVG e sempre numa cor só
// (fill="currentColor": herda a cor do texto do cartão), como na referência.
//
// São desenhos simples feitos aqui, não os arquivos oficiais das bandeiras.
// Ficam fora do icons.tsx porque não são ícones de traço 24x24: cada marca tem
// a sua largura, e a altura (24) é o que elas têm em comum.
//
// Os nomes usam peso 800 e inclinação, que o resto da interface evita: aqui é
// o desenho de uma marca, não texto do app.
export function BrandMark({ brand }: {
  brand: CardBrand | null // null = cartão antigo, ainda sem bandeira
}) {
  switch (brand) {
    case 'VISA':
      return (
        <svg className="card-brand" viewBox="0 0 62 24" fill="currentColor" aria-hidden="true">
          {/* skewX inclina as letras; o x começa adiante para compensar o desvio. */}
          <text className="card-brand-word" x="5.5" y="21" fontSize="25" transform="skewX(-14)">VISA</text>
        </svg>
      )
    case 'MASTERCARD':
      // Dois círculos sobrepostos; a transparência do segundo faz o "encontro" dos dois.
      return (
        <svg className="card-brand" viewBox="0 0 39 24" fill="currentColor" aria-hidden="true">
          <circle cx="12" cy="12" r="12" opacity="0.95" />
          <circle cx="27" cy="12" r="12" opacity="0.5" />
        </svg>
      )
    case 'ELO':
      return (
        <svg className="card-brand" viewBox="0 0 40 24" fill="currentColor" aria-hidden="true">
          <text className="card-brand-word" x="0" y="20" fontSize="25">elo</text>
        </svg>
      )
    case 'AMEX':
      return (
        <svg className="card-brand" viewBox="0 0 56 24" fill="currentColor" aria-hidden="true">
          <rect x="1" y="1" width="54" height="22" rx="5" fill="none" stroke="currentColor" strokeWidth="2" />
          <text className="card-brand-word" x="28" y="16.5" fontSize="13" textAnchor="middle" letterSpacing="1">AMEX</text>
        </svg>
      )
    case 'HIPERCARD':
      return (
        <svg className="card-brand" viewBox="0 0 98 24" fill="currentColor" aria-hidden="true">
          <text className="card-brand-word" x="4.5" y="19" fontSize="19" transform="skewX(-12)">Hipercard</text>
        </svg>
      )
    default:
      // "Outra" bandeira ou cartão sem os dados: o ícone genérico de cartão
      // (o mesmo desenho do CreditCard do icons.tsx).
      return (
        <svg className="card-brand" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <rect x="2" y="5" width="20" height="14" rx="2" />
          <path d="M2 10h20" />
        </svg>
      )
  }
}
