import { describe, expect, it } from 'vitest'
import { keyboardInset } from './viewport'

describe('keyboardInset', () => {
  it('é zero sem teclado', () => {
    expect(keyboardInset(844, 844, 0)).toBe(0)
  })

  it('é a altura coberta pelo teclado', () => {
    expect(keyboardInset(844, 508, 0)).toBe(336)
  })

  it('desconta a rolagem que o iOS faz para mostrar o campo', () => {
    expect(keyboardInset(844, 508, 120)).toBe(216)
  })

  it('arredonda para cima medidas fracionadas', () => {
    expect(keyboardInset(844, 507.6, 0)).toBe(337)
  })

  it('nunca é negativo', () => {
    expect(keyboardInset(844, 600, 400)).toBe(0)
  })
})
