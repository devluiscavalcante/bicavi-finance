// Funções puras sobre listas, usadas pelas telas (e testadas em lists.test.ts).

// Ordem alfabética respeitando acentos ("Água" junto do "A", não depois do "Z").
// Devolve uma lista nova: o estado do React nunca é alterado no lugar.
export function sortByName<T extends { name: string }>(list: readonly T[]): T[] {
  return [...list].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

// Agrupa por dia mantendo a ordem da API (mais recentes primeiro):
// itens SEGUIDOS com a mesma data formam um grupo.
export function groupByDay<T extends { occurredOn: string }>(list: readonly T[]) {
  const groups: { date: string; items: T[] }[] = []
  for (const item of list) {
    const last = groups.at(-1)
    if (last && last.date === item.occurredOn) {
      last.items.push(item)
    } else {
      groups.push({ date: item.occurredOn, items: [item] })
    }
  }
  return groups
}
