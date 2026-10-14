// Contas do gesto "puxar para atualizar" (ver PullToRefresh.tsx), separadas
// do componente para poderem ser testadas sem navegador.

// Distância do indicador (já amortecida) que, ao soltar, dispara a atualização.
export const PULL_THRESHOLD = 70
const PULL_MAX = 110
// O indicador anda metade do que o dedo anda: dá a sensação de "elástico".
const RESISTANCE = 0.5

export function pullDistance(dy: number): number {
  return Math.min(PULL_MAX, Math.max(0, dy * RESISTANCE))
}
