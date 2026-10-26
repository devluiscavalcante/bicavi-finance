import { describe, expect, it } from 'vitest'
import { barPercent, trendRange, trendScale } from './trend'

const period = { firstEditableMonth: '2026-10', oldestVisibleMonth: '2026-04', lastMonth: '2031-10' }

describe('trendRange', () => {
  it('pega 6 meses antes e 6 depois do mês da tela', () => {
    expect(trendRange('2026-11', period)).toEqual({ from: '2026-05', to: '2027-05' })
  })

  it('não passa do mês mais antigo que a API devolve', () => {
    expect(trendRange('2026-06', period)).toEqual({ from: '2026-04', to: '2026-12' })
  })

  it('não passa do horizonte de planejamento', () => {
    expect(trendRange('2031-08', period)).toEqual({ from: '2031-02', to: '2031-10' })
  })
})

describe('trendScale', () => {
  it('divide a altura na proporção da maior sobra e da maior falta', () => {
    expect(trendScale([3000, -1000, 500])).toEqual({ above: 3000, below: 1000 })
  })

  it('só sobra: nada abaixo da linha', () => {
    expect(trendScale([100, 0])).toEqual({ above: 100, below: 0 })
  })

  it('tudo zerado: a linha do zero fica embaixo', () => {
    expect(trendScale([0, 0])).toEqual({ above: 1, below: 0 })
    expect(trendScale([])).toEqual({ above: 1, below: 0 })
  })
})

describe('barPercent', () => {
  const scale = { above: 3000, below: 1000 }

  it('mede cada barra pelo maior valor do lado dela', () => {
    expect(barPercent(3000, scale)).toBe(100)
    expect(barPercent(1500, scale)).toBe(50)
    expect(barPercent(-500, scale)).toBe(50)
  })

  it('saldo zero não tem barra', () => {
    expect(barPercent(0, trendScale([0]))).toBe(0)
  })
})
