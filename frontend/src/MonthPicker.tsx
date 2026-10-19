import { useEffect, useRef } from 'react'
import { currentMonth, formatMonth, formatMonthShort, monthRange } from './format'

// Carrossel de meses (estilo roda do Relógio do iPhone), aberto ao segurar o
// título do mês. Rola na horizontal, cada mês "trava" no centro (CSS scroll-snap)
// e tocar num mês vai para ele. Tocar fora ou Esc fecha sem trocar.
// Usa o <dialog> nativo, como o TransactionSheet: showModal() bloqueia o fundo.
export function MonthPicker({ month, from, to, top, dragPointerId, onSelect, onClose }: {
  month: string // mês na tela: abre centralizado nele
  from: string // limites vindos do GET /api/period
  to: string
  top: number // posição vertical (px), logo abaixo do título
  dragPointerId: number | null // dedo que abriu o picker e ainda pode arrastar a faixa
  onSelect: (month: string) => void
  onClose: () => void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const stripRef = useRef<HTMLDivElement>(null)
  // O dedo que abriu o picker (segurando o título) ainda está na tela; ao soltar,
  // o navegador pode gerar um "click" aqui dentro. Só aceitamos cliques que
  // começaram com um toque novo dentro do picker.
  const touchedInside = useRef(false)
  const centered = useRef(-1)
  const months = monthRange(from, to)
  const today = currentMonth()

  // Aproxima de 0 o mês no centro e de 3 os mais distantes. O CSS usa esse
  // número (--d) para encolher e apagar os meses, como uma roda girando.
  function updateDistances() {
    const strip = stripRef.current
    if (!strip) return
    const center = strip.scrollLeft + strip.clientWidth / 2
    const items = strip.children as HTMLCollectionOf<HTMLElement>
    let closest = 0
    let closestDistance = Infinity
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      const d = (item.offsetLeft + item.offsetWidth / 2 - center) / item.offsetWidth
      item.style.setProperty('--d', String(Math.min(Math.abs(d), 3)))
      if (Math.abs(d) < closestDistance) {
        closestDistance = Math.abs(d)
        closest = i
      }
    }
    // Um "tique" de vibração a cada mês que passa pelo centro.
    // O Safari do iPhone não tem vibrate(), por isso o "?.".
    if (centered.current !== -1 && closest !== centered.current) navigator.vibrate?.(5)
    centered.current = closest
  }

  useEffect(() => {
    const dialog = dialogRef.current
    // showModal() foca o primeiro botão do dialog (o mês mais antigo), e focar
    // rola a faixa até ele. No Safari essa rolagem chega DEPOIS da nossa e o
    // picker abria no primeiro mês. Com autofocus no próprio <dialog>, o foco
    // fica nele e nada rola sozinho. (setAttribute porque o autoFocus do React
    // chama focus() por conta própria em vez de pôr o atributo no HTML.)
    dialog?.setAttribute('autofocus', '')
    // StrictMode roda efeitos duas vezes em dev: showModal() num dialog aberto lança erro.
    if (dialog && !dialog.open) dialog.showModal()

    // Abre já com o mês atual da tela no centro (sem animação de rolagem).
    function centerSelected() {
      const strip = stripRef.current
      const selected = strip?.querySelector<HTMLElement>('[aria-current="date"]')
      if (strip && selected) {
        strip.scrollLeft = selected.offsetLeft - (strip.clientWidth - selected.offsetWidth) / 2
      }
      updateDistances()
    }
    centerSelected()
    // De novo no próximo quadro, caso alguma rolagem atrasada do navegador ainda chegue.
    const frame = requestAnimationFrame(centerSelected)
    return () => cancelAnimationFrame(frame)
  }, [])

  // Segurou o título, o picker abriu e o dedo continua na tela: arrastar para os
  // lados gira a faixa direto, sem precisar soltar e tocar de novo. Esse toque
  // começou no título, então a rolagem nativa da faixa não o recebe; movemos
  // o scrollLeft à mão. Ao soltar, a faixa para no mês mais próximo do centro.
  useEffect(() => {
    const strip = stripRef.current
    if (dragPointerId === null || !strip) return
    let lastX: number | null = null
    // Durante o arrasto o scroll-snap brigaria com cada passo: fica desligado.
    strip.classList.add('dragging')

    function handleMove(e: PointerEvent) {
      if (e.pointerId !== dragPointerId || !strip) return
      if (lastX !== null) strip.scrollLeft -= e.clientX - lastX
      lastX = e.clientX
    }

    function handleUp(e: PointerEvent) {
      if (e.pointerId !== dragPointerId) return
      stop()
      const item = strip?.children[centered.current] as HTMLElement | undefined
      if (strip && item) {
        strip.scrollTo({
          left: item.offsetLeft - (strip.clientWidth - item.offsetWidth) / 2,
          behavior: 'smooth',
        })
      }
    }

    function stop() {
      strip?.classList.remove('dragging')
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
      window.removeEventListener('pointercancel', handleUp)
    }

    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    window.addEventListener('pointercancel', handleUp)
    return stop
  }, [dragPointerId])

  function choose(target: string) {
    if (!touchedInside.current) return
    onSelect(target)
  }

  return (
    <dialog
      ref={dialogRef}
      className="month-picker"
      style={{ top }}
      aria-label="Escolher mês"
      onClose={onClose}
      onPointerDown={() => { touchedInside.current = true }}
      // Clique no fundo: o alvo é o próprio <dialog> (o conteúdo cobre o resto).
      onClick={e => {
        if (e.target === e.currentTarget && touchedInside.current) dialogRef.current?.close()
      }}
    >
      <div className="month-picker-body">
        <div ref={stripRef} className="month-strip" onScroll={updateDistances}>
          {months.map(m => (
            <button
              key={m}
              type="button"
              className="month-item"
              aria-current={m === month ? 'date' : undefined}
              aria-label={formatMonth(m)}
              onClick={() => choose(m)}
            >
              <strong>{formatMonthShort(m)}</strong>
              <small>{m.slice(0, 4)}</small>
            </button>
          ))}
        </div>
        {today !== month && today >= from && today <= to && (
          <button type="button" className="month-picker-today" onClick={() => choose(today)}>
            Voltar para hoje
          </button>
        )}
      </div>
    </dialog>
  )
}
