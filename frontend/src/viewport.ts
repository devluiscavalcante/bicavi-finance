// Altura da área que a pessoa realmente vê, para o layout caber na tela.
//
// 100dvh não serve no iPhone: com o teclado aberto ele continua com a altura da
// tela inteira (o teclado fica por cima, e o painel preso embaixo some atrás
// dele), e no app instalado na Tela de Início pode passar da área visível. O
// visualViewport é a parte visível de verdade: encolhe com o teclado e anda
// quando o iOS rola a página para mostrar o campo focado.

// Quanto do fundo da página o teclado cobre: a página vai até innerHeight, a
// parte visível termina em offsetTop + height. Arredonda para cima para não
// sobrar 1px de fresta; nunca negativo (zoom com pinça pode inverter a conta).
export function keyboardInset(innerHeight: number, viewportHeight: number, viewportOffsetTop: number): number {
  return Math.max(0, Math.ceil(innerHeight - viewportHeight - viewportOffsetTop))
}

// Publica as medidas como variáveis CSS no <html>, atualizadas a cada mudança:
// --app-height (altura visível) e --keyboard-inset (espaço coberto embaixo).
// O CSS usa 100dvh como reserva enquanto elas não existem.
export function trackViewport() {
  const viewport = window.visualViewport
  if (!viewport) return // navegador antigo: fica com o 100dvh do CSS

  const style = document.documentElement.style
  function update() {
    style.setProperty('--app-height', `${viewport!.height}px`)
    style.setProperty('--keyboard-inset', `${keyboardInset(window.innerHeight, viewport!.height, viewport!.offsetTop)}px`)
  }

  update()
  // resize: teclado abrindo/fechando, girar o celular. scroll: o iOS rolando a
  // parte visível até o campo focado (muda o offsetTop).
  viewport.addEventListener('resize', update)
  viewport.addEventListener('scroll', update)
}
