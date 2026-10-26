import { useEffect, useRef } from 'react'
import { getTheme, onThemeChange } from './theme'

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
}

const LINK_DISTANCE = 120 // px: abaixo disso, dois pontos ganham uma linha
const POINTER_DISTANCE = 160
const MAX_PARTICLES = 90

// Fundo animado das telas de login/cadastro: pontos que flutuam e se conectam
// por linhas quando estão próximos. Desenhado num <canvas> a cada quadro.
export function ParticleField() {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const pointer = { x: -9999, y: -9999 }
    let particles: Particle[] = []
    let width = 0
    let height = 0
    let frame = 0

    // Cor do tema (--particle no index.css): cinza-escuro no claro, quase branco
    // no escuro, pink no rosa. Lida de novo só quando o tema muda, não a cada quadro.
    let color = ''
    let hearts = false
    function readTheme() {
      color = getComputedStyle(document.documentElement).getPropertyValue('--particle').trim()
      hearts = getTheme() === 'pink'
    }

    function resize() {
      width = window.innerWidth
      height = window.innerHeight
      // Telas "retina" têm mais pixels físicos que CSS: sem isto o desenho fica borrado.
      const dpr = window.devicePixelRatio || 1
      canvas!.width = width * dpr
      canvas!.height = height * dpr
      ctx!.setTransform(dpr, 0, 0, dpr, 0, 0)

      // Quantidade proporcional à área: celular tem menos pontos que monitor.
      const count = Math.min(MAX_PARTICLES, Math.floor((width * height) / 14000))
      particles = Array.from({ length: count }, () => ({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.4,
        vy: (Math.random() - 0.5) * 0.4,
      }))
    }

    function draw() {
      ctx!.clearRect(0, 0, width, height)

      for (const p of particles) {
        if (!reducedMotion) {
          p.x += p.vx
          p.y += p.vy
          // Bateu na borda: inverte a direção.
          if (p.x < 0 || p.x > width) p.vx *= -1
          if (p.y < 0 || p.y > height) p.vy *= -1

          // Cursor por perto: empurra o ponto de leve para longe.
          const dx = p.x - pointer.x
          const dy = p.y - pointer.y
          const dist = Math.hypot(dx, dy)
          if (dist < POINTER_DISTANCE && dist > 0) {
            const force = (1 - dist / POINTER_DISTANCE) * 0.6
            p.x += (dx / dist) * force
            p.y += (dy / dist) * force
          }
        }

        ctx!.fillStyle = `rgb(${color} / ${hearts ? 0.5 : 0.35})`
        if (hearts) {
          drawHeart(ctx!, p.x, p.y, 4)
        } else {
          ctx!.beginPath()
          ctx!.arc(p.x, p.y, 1.6, 0, Math.PI * 2)
          ctx!.fill()
        }
      }

      // Linhas: quanto mais perto, mais forte. Cada par é comparado uma vez (j > i).
      ctx!.lineWidth = 1
      for (let i = 0; i < particles.length; i++) {
        const a = particles[i]
        for (let j = i + 1; j < particles.length; j++) {
          const b = particles[j]
          const dist = Math.hypot(a.x - b.x, a.y - b.y)
          if (dist < LINK_DISTANCE) {
            ctx!.strokeStyle = `rgb(${color} / ${(1 - dist / LINK_DISTANCE) * 0.18})`
            ctx!.beginPath()
            ctx!.moveTo(a.x, a.y)
            ctx!.lineTo(b.x, b.y)
            ctx!.stroke()
          }
        }
        const toPointer = Math.hypot(a.x - pointer.x, a.y - pointer.y)
        if (toPointer < POINTER_DISTANCE) {
          ctx!.strokeStyle = `rgb(${color} / ${(1 - toPointer / POINTER_DISTANCE) * 0.35})`
          ctx!.beginPath()
          ctx!.moveTo(a.x, a.y)
          ctx!.lineTo(pointer.x, pointer.y)
          ctx!.stroke()
        }
      }

      // requestAnimationFrame: o navegador chama no próximo quadro (~60fps)
      // e pausa sozinho quando a aba fica em segundo plano.
      if (!reducedMotion) frame = requestAnimationFrame(draw)
    }

    function onPointerMove(e: PointerEvent) {
      pointer.x = e.clientX
      pointer.y = e.clientY
    }

    function onPointerLeave() {
      pointer.x = -9999
      pointer.y = -9999
    }

    // Com movimento reduzido, o desenho é estático: redesenha só quando algo muda.
    function redrawIfStatic() {
      if (reducedMotion) draw()
    }

    function onThemeChanged() {
      readTheme()
      redrawIfStatic()
    }

    function onResize() {
      resize()
      redrawIfStatic()
    }

    readTheme()
    resize()
    draw()
    const stopThemeListener = onThemeChange(onThemeChanged)
    window.addEventListener('resize', onResize)
    window.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerleave', onPointerLeave)

    // Cleanup: ao sair da tela de login, para a animação e remove os listeners.
    // Sem isto, o loop continuaria rodando para sempre em segundo plano.
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('resize', onResize)
      window.removeEventListener('pointermove', onPointerMove)
      document.removeEventListener('pointerleave', onPointerLeave)
      stopThemeListener()
    }
  }, [])

  return <canvas ref={canvasRef} className="particles" aria-hidden="true" />
}

// Coração pequeno centrado em (x, y): duas curvas de Bézier, uma para cada metade.
function drawHeart(ctx: CanvasRenderingContext2D, x: number, y: number, size: number) {
  ctx.beginPath()
  ctx.moveTo(x, y + size * 0.9)
  ctx.bezierCurveTo(x - size * 1.6, y - size * 0.2, x - size * 0.6, y - size * 1.3, x, y - size * 0.4)
  ctx.bezierCurveTo(x + size * 0.6, y - size * 1.3, x + size * 1.6, y - size * 0.2, x, y + size * 0.9)
  ctx.fill()
}
