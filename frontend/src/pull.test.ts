import { describe, expect, it } from 'vitest'
import { PULL_THRESHOLD, pullDistance } from './pull'

describe('pullDistance', () => {
  it('o indicador anda metade do que o dedo anda', () => {
    expect(pullDistance(100)).toBe(50)
  })

  it('arrastar para cima não puxa nada', () => {
    expect(pullDistance(-40)).toBe(0)
  })

  it('tem um limite, por mais que o dedo desça', () => {
    expect(pullDistance(1000)).toBe(110)
  })

  it('atualiza com um arrasto de 140px, mas não com um toque descuidado', () => {
    expect(pullDistance(140)).toBeGreaterThanOrEqual(PULL_THRESHOLD)
    expect(pullDistance(60)).toBeLessThan(PULL_THRESHOLD)
  })
})
