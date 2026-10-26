import { describe, expect, it } from 'vitest'
import { nextTheme, parseTheme, THEMES } from './theme'

describe('nextTheme', () => {
  it('segue a ordem claro, escuro, rosa', () => {
    expect(nextTheme('light')).toBe('dark')
    expect(nextTheme('dark')).toBe('pink')
  })

  it('volta ao primeiro depois do último', () => {
    expect(nextTheme('pink')).toBe('light')
  })

  it('passa por todos os temas e volta ao início', () => {
    let theme = THEMES[0]
    const seen = []
    for (let i = 0; i < THEMES.length; i++) {
      seen.push(theme)
      theme = nextTheme(theme)
    }
    expect(seen).toEqual(THEMES)
    expect(theme).toBe(THEMES[0])
  })
})

describe('parseTheme', () => {
  it('aceita os temas que existem', () => {
    expect(parseTheme('light')).toBe('light')
    expect(parseTheme('dark')).toBe('dark')
    expect(parseTheme('pink')).toBe('pink')
  })

  it('recusa valor desconhecido ou ausente', () => {
    expect(parseTheme('roxo')).toBeNull()
    expect(parseTheme('')).toBeNull()
    expect(parseTheme(null)).toBeNull()
  })
})
