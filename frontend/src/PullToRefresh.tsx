import { useEffect, useRef, useState } from 'react'
import { RefreshCw } from './icons'
import { PULL_THRESHOLD, pullDistance } from './pull'

// Antes disso o dedo mal saiu do lugar e ainda não dá para saber a direção.
const DIRECTION_LOCK_PX = 10

// "Puxar para atualizar": com a página no topo, arrastar o dedo para baixo e soltar.
// O app instalado no iPhone (PWA) não tem esse gesto nativo, então ele é feito aqui.
//
// Fica num componente separado de propósito: a cada movimento do dedo só ESTE
// componente (o indicador) é redesenhado, e não a tela do mês inteira.
export function PullToRefresh({ onRefresh, disabled }: {
  onRefresh: () => Promise<void>
  disabled: boolean // ex.: com o painel aberto, o gesto não vale
}) {
  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)

  // A função muda a cada render da página; guardá-la num ref evita refazer
  // os listeners (e perder um gesto no meio) toda vez que isso acontece.
  const onRefreshRef = useRef(onRefresh)
  useEffect(() => {
    onRefreshRef.current = onRefresh
  })

  useEffect(() => {
    if (disabled || refreshing) return

    let start: { x: number; y: number } | null = null
    let distance = 0

    function handleStart(e: TouchEvent) {
      // Só vale com a página no topo: no meio dela, arrastar para baixo é rolar.
      start = window.scrollY <= 0 && e.touches.length === 1
        ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
        : null
      distance = 0
    }

    function handleMove(e: TouchEvent) {
      if (!start) return
      const dx = e.touches[0].clientX - start.x
      const dy = e.touches[0].clientY - start.y
      if (distance === 0) {
        if (Math.hypot(dx, dy) < DIRECTION_LOCK_PX) return
        // Para cima ou mais horizontal que vertical: é rolagem ou troca de mês.
        if (dy <= 0 || Math.abs(dx) > dy) {
          start = null
          return
        }
      }
      distance = pullDistance(dy)
      setPull(distance)
    }

    function handleEnd() {
      if (!start) return
      start = null
      if (distance < PULL_THRESHOLD) {
        setPull(0)
        return
      }
      setRefreshing(true)
      setPull(PULL_THRESHOLD)
      onRefreshRef.current().finally(() => {
        setRefreshing(false)
        setPull(0)
      })
    }

    // passive: avisa o navegador que não vamos bloquear a rolagem (fica mais fluido).
    window.addEventListener('touchstart', handleStart, { passive: true })
    window.addEventListener('touchmove', handleMove, { passive: true })
    window.addEventListener('touchend', handleEnd)
    window.addEventListener('touchcancel', handleEnd)
    return () => {
      window.removeEventListener('touchstart', handleStart)
      window.removeEventListener('touchmove', handleMove)
      window.removeEventListener('touchend', handleEnd)
      window.removeEventListener('touchcancel', handleEnd)
    }
  }, [disabled, refreshing])

  if (pull === 0 && !refreshing) return null

  const progress = Math.min(1, pull / PULL_THRESHOLD)
  return (
    <div className="ptr" role="status" aria-label={refreshing ? 'Atualizando' : 'Solte para atualizar'}
         style={{ transform: `translate(-50%, ${pull}px)`, opacity: progress }}>
      {/* Enquanto puxa, o ícone gira junto com o dedo; atualizando, gira sozinho. */}
      <span className={refreshing ? 'ptr-icon spinning' : 'ptr-icon'}
            style={refreshing ? undefined : { transform: `rotate(${progress * 270}deg)` }}>
        <RefreshCw />
      </span>
    </div>
  )
}
