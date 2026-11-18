import { describe, expect, it } from 'vitest'
import {
  describeCard, formatExpiry, formatExpiryInput, isComplete, lastFourInput, maskedNumber, parseExpiry,
  stackPositions,
} from './cards'
import type { CardResponse } from './types'

const nubank: CardResponse = {
  id: 1, name: 'Nubank', brand: 'MASTERCARD', holderName: 'Maria Silva', lastFour: '3210',
  expiryMonth: 11, expiryYear: 2029, color: 'ROXO',
}

// Cadastrado antes de existirem os outros dados: só id e nome.
const oldCard: CardResponse = {
  id: 2, name: 'Cartão antigo', brand: null, holderName: null, lastFour: null,
  expiryMonth: null, expiryYear: null, color: null,
}

describe('isComplete', () => {
  it('cartão com todos os dados está completo; o antigo, só com nome, não', () => {
    expect(isComplete(nubank)).toBe(true)
    expect(isComplete(oldCard)).toBe(false)
  })
})

describe('maskedNumber', () => {
  it('mostra só os 4 últimos dígitos', () => {
    expect(maskedNumber('3210')).toBe('•••• •••• •••• 3210')
  })

  it('mantém os zeros à esquerda', () => {
    expect(maskedNumber('0042')).toBe('•••• •••• •••• 0042')
  })

  it('completa com pontos o que ainda não foi digitado', () => {
    expect(maskedNumber('32')).toBe('•••• •••• •••• 32••')
    expect(maskedNumber('')).toBe('•••• •••• •••• ••••')
  })
})

describe('lastFourInput', () => {
  it('aceita só dígitos, até 4', () => {
    expect(lastFourInput('', '3')).toBe('3')
    expect(lastFourInput('321', '3210')).toBe('3210')
    expect(lastFourInput('32', '32a')).toBe('32')
    expect(lastFourInput('3210', '')).toBe('')
  })

  it('ignora o 5º dígito digitado', () => {
    expect(lastFourInput('3210', '32105')).toBe('3210')
  })

  it('ao colar o número inteiro, fica só com os 4 últimos', () => {
    expect(lastFourInput('', '4111 1111 1111 3210')).toBe('3210')
    expect(lastFourInput('9999', '4111111111113210')).toBe('3210')
  })
})

describe('formatExpiry', () => {
  it('escreve mês e ano com dois dígitos, como no cartão', () => {
    expect(formatExpiry(11, 2029)).toBe('11/29')
    expect(formatExpiry(3, 2031)).toBe('03/31')
    expect(formatExpiry(12, 2005)).toBe('12/05')
  })
})

describe('formatExpiryInput', () => {
  it('põe a barra sozinha depois do mês', () => {
    expect(formatExpiryInput('1')).toBe('1')
    expect(formatExpiryInput('11')).toBe('11')
    expect(formatExpiryInput('112')).toBe('11/2')
    expect(formatExpiryInput('1129')).toBe('11/29')
  })

  it('aceita o que já está formatado e ignora o que não é dígito', () => {
    expect(formatExpiryInput('11/29')).toBe('11/29')
    expect(formatExpiryInput('11-29')).toBe('11/29')
    expect(formatExpiryInput('ab')).toBe('')
  })

  it('corta o que passar de 4 dígitos', () => {
    expect(formatExpiryInput('11/2029')).toBe('11/20')
  })

  it('completa com zero o mês de um dígito', () => {
    expect(formatExpiryInput('5')).toBe('05') // nenhum mês começa com 5
    expect(formatExpiryInput('3/')).toBe('03') // a barra some até vir o ano
    expect(formatExpiryInput('1/29')).toBe('01/29')
  })

  it('completa com zero também o mês colado de uma vez', () => {
    expect(formatExpiryInput('429')).toBe('04/29')
    expect(formatExpiryInput('4/29')).toBe('04/29')
    expect(formatExpiryInput('0429')).toBe('04/29')
  })

  it('apagar funciona: a barra some junto com o primeiro dígito do ano', () => {
    expect(formatExpiryInput('11/')).toBe('11')
    expect(formatExpiryInput('0')).toBe('0')
    expect(formatExpiryInput('')).toBe('')
  })
})

describe('parseExpiry', () => {
  it('lê MM/AA e completa o século', () => {
    expect(parseExpiry('11/29')).toEqual({ month: 11, year: 2029 })
    expect(parseExpiry('01/30')).toEqual({ month: 1, year: 2030 })
    expect(parseExpiry(' 12/00 ')).toEqual({ month: 12, year: 2000 })
  })

  it('recusa mês que não existe', () => {
    expect(parseExpiry('00/29')).toBeNull()
    expect(parseExpiry('13/29')).toBeNull()
  })

  it('recusa o que está pela metade ou fora do formato', () => {
    expect(parseExpiry('')).toBeNull()
    expect(parseExpiry('11')).toBeNull()
    expect(parseExpiry('11/2')).toBeNull()
    expect(parseExpiry('1/29')).toBeNull()
    expect(parseExpiry('11/2029')).toBeNull()
  })

  it('volta ao mesmo texto passando por formatExpiry', () => {
    const { month, year } = parseExpiry('07/31')!

    expect(formatExpiry(month, year)).toBe('07/31')
  })
})

describe('stackPositions', () => {
  const positions = (ids: number[], frontId: number | null) => [...stackPositions(ids, frontId)]

  it('sem escolha, a pilha segue a lista e o último fica na frente', () => {
    expect(positions([10, 20, 30], null)).toEqual([[10, 0], [20, 1], [30, 2]])
  })

  it('o cartão escolhido vai para a frente e os outros mantêm a ordem', () => {
    expect(positions([10, 20, 30], 10)).toEqual([[20, 0], [30, 1], [10, 2]])
    expect(positions([10, 20, 30], 20)).toEqual([[10, 0], [30, 1], [20, 2]])
  })

  it('escolhido que não existe mais (foi excluído) vale como sem escolha', () => {
    expect(positions([10, 20], 99)).toEqual([[10, 0], [20, 1]])
  })

  it('funciona com um cartão só e com nenhum', () => {
    expect(positions([10], null)).toEqual([[10, 0]])
    expect(positions([], null)).toEqual([])
  })
})

describe('describeCard', () => {
  it('diz nome, bandeira e final', () => {
    expect(describeCard(nubank)).toBe('Nubank, Mastercard, final 3210')
  })

  it('avisa quando o cartão é antigo e está sem os dados', () => {
    expect(describeCard(oldCard)).toBe('Cartão antigo, dados incompletos')
  })
})
