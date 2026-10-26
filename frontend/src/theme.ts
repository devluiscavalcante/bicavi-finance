import { useSyncExternalStore } from 'react'

// Tema visual do app. Fica salvo no aparelho (localStorage): cada celular
// lembra o seu, e o backend não precisa saber disso.
export type Theme = 'light' | 'dark' | 'pink'

// Ordem do botão: cada toque passa para o próximo e o último volta ao primeiro.
export const THEMES: readonly Theme[] = ['light', 'dark', 'pink']

export const THEME_LABELS: Record<Theme, string> = {
  light: 'Claro',
  dark: 'Escuro',
  pink: 'Rosa',
}

// Cor da barra de status do celular em cada tema (o mesmo --bg do index.css).
const STATUS_BAR: Record<Theme, string> = {
  light: '#fafafa',
  dark: '#09090b',
  pink: '#fff1f6',
}

const STORAGE_KEY = 'bicavi.theme'

export function nextTheme(theme: Theme): Theme {
  return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length]
}

// O localStorage pode ter qualquer coisa (versão antiga, edição à mão):
// só aceita um tema que existe.
export function parseTheme(value: string | null): Theme | null {
  return THEMES.includes(value as Theme) ? (value as Theme) : null
}

// Sem escolha salva, segue o sistema (claro ou escuro), como era antes do botão.
function systemTheme(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

// localStorage pode lançar exceção (Safari em modo privado antigo, cota cheia):
// sem ele o tema só não é lembrado, o app continua funcionando.
function savedTheme(): Theme | null {
  try {
    return parseTheme(localStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}

// ---------- Store ----------
// Um valor fora do React com uma lista de quem quer saber quando ele muda.
// useSyncExternalStore liga isso aos componentes: o botão do topo e o da tela
// de login leem o mesmo tema sem precisar de Context.
let current: Theme = 'light'
const listeners = new Set<() => void>()

function apply(theme: Theme) {
  current = theme
  // O CSS troca as variáveis de cor por :root[data-theme="..."].
  document.documentElement.dataset.theme = theme
  document.querySelectorAll('meta[name="theme-color"]')
    .forEach(meta => meta.setAttribute('content', STATUS_BAR[theme]))
  listeners.forEach(listener => listener())
}

export function setTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // sem localStorage: vale só até fechar o app
  }
  apply(theme)
}

// Chamado uma vez no main.tsx, antes do primeiro desenho do React.
export function initTheme() {
  apply(savedTheme() ?? systemTheme())
  // Enquanto a pessoa não escolheu nada, continua acompanhando o sistema
  // (ex.: o iPhone muda para escuro à noite).
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (!savedTheme()) apply(systemTheme())
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getTheme(): Theme {
  return current
}

// Tema atual, redesenhando o componente quando ele muda.
export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, getTheme)
}

export function onThemeChange(listener: () => void): () => void {
  return subscribe(listener)
}
