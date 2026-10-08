import { describe, expect, it } from 'vitest'
import { groupByDay, sortByName } from './lists'

describe('sortByName', () => {
  it('ordena respeitando acentos e maiúsculas do português', () => {
    const sorted = sortByName([{ name: 'banco' }, { name: 'Água' }, { name: 'Casa' }, { name: 'Ábaco' }])

    expect(sorted.map(c => c.name)).toEqual(['Ábaco', 'Água', 'banco', 'Casa'])
  })

  it('não altera a lista original (estado do React é imutável)', () => {
    const original = [{ name: 'B' }, { name: 'A' }]

    sortByName(original)

    expect(original.map(c => c.name)).toEqual(['B', 'A'])
  })
})

describe('groupByDay', () => {
  const tx = (id: number, occurredOn: string) => ({ id, occurredOn })

  it('agrupa itens seguidos do mesmo dia, mantendo a ordem da API', () => {
    const groups = groupByDay([tx(1, '2026-10-08'), tx(2, '2026-10-08'), tx(3, '2026-10-05')])

    expect(groups).toEqual([
      { date: '2026-10-08', items: [tx(1, '2026-10-08'), tx(2, '2026-10-08')] },
      { date: '2026-10-05', items: [tx(3, '2026-10-05')] },
    ])
  })

  // A API já devolve ordenado por data; o agrupamento confia nisso e não reordena.
  it('só junta dias SEGUIDOS: o mesmo dia separado por outro vira outro grupo', () => {
    const groups = groupByDay([tx(1, '2026-10-08'), tx(2, '2026-10-05'), tx(3, '2026-10-08')])

    expect(groups.map(g => g.date)).toEqual(['2026-10-08', '2026-10-05', '2026-10-08'])
  })

  it('lista vazia não tem grupos', () => {
    expect(groupByDay([])).toEqual([])
  })
})
