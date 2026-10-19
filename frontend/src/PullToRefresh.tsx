import { useEffect, useRef, useState } from 'react'
import { RefreshCw } from './icons'
import { PULL_THRESHOLD, pullDistance } from './pull'

// Antes disso o dedo mal saiu do lugar e ainda não dá para saber a direção.
const DIRECTION_LOCK_PX = 10
// Com o cache, a atualização pode terminar em ~100 ms e o ícone só piscaria.
// Ele gira pelo menos esse tempo, para dar para ver que algo aconteceu.
const MIN_SPIN_MS = 400
// Duração da volta do indicador para cima (a mesma transição do .ptr.settling no CSS).
const SETTLE_MS = 450

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

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
  // Dedo arrastando: o indicador segue o dedo, sem transição. Solto: ele desliza.
  const [dragging, setDragging] = useState(false)
  // Voltando para cima depois de soltar. Fica na tela até a animação acabar:
  // 'done' depois de atualizar (o ícone continua girando enquanto some),
  // 'cancel' quando soltou antes do ponto de atualizar.
  const [closing, setClosing] = useState<'done' | 'cancel' | null>(null)

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
      setDragging(true)
      setClosing(null)
      setPull(distance)
    }

    function handleEnd() {
      if (!start) return
      start = null
      setDragging(false)
      if (distance < PULL_THRESHOLD) {
        setClosing('cancel')
        setPull(0)
        return
      }
      setRefreshing(true)
      setPull(PULL_THRESHOLD)
      Promise.all([onRefreshRef.current(), wait(MIN_SPIN_MS)]).finally(() => {
        setRefreshing(false)
        setClosing('done')
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

  // Terminada a volta, tira o indicador da tela. Um timer, e não o evento
  // transitionend: com "reduzir movimento" ligado não há transição e o evento
  // nunca dispararia.
  useEffect(() => {
    if (!closing) return
    const timer = setTimeout(() => setClosing(null), SETTLE_MS)
    return () => clearTimeout(timer)
  }, [closing])

  if (pull === 0 && !refreshing && !closing) return null

  const progress = Math.min(1, pull / PULL_THRESHOLD)
  const spinning = refreshing || closing === 'done'
  return (
    <div className={dragging ? 'ptr' : 'ptr settling'} role="status"
         aria-label={refreshing ? 'Atualizando' : 'Solte para atualizar'}
         // Cresce de 60% a 100% conforme puxa (e encolhe de volta ao sumir).
         style={{ transform: `translate(-50%, ${pull}px) scale(${0.6 + 0.4 * progress})`, opacity: progress }}>
      {/* Enquanto puxa, o ícone gira junto com o dedo; atualizando, gira sozinho. */}
      <span className={spinning ? 'ptr-icon spinning' : 'ptr-icon'}
            style={spinning ? undefined : { transform: `rotate(${progress * 270}deg)` }}>
        <RefreshCw />
      </span>
    </div>
  )
}
