import type { JSX } from 'react'
import { Cat, Moon, Sun } from './icons'
import { nextTheme, setTheme, THEME_LABELS, useTheme, type Theme } from './theme'

const ICONS: Record<Theme, () => JSX.Element> = {
  light: Sun,
  dark: Moon,
  pink: Cat,
}

// Botão redondo que alterna o tema a cada toque: claro → escuro → rosa → claro.
// O ícone mostra o tema ATUAL; o rótulo diz qual vem a seguir.
// key={theme}: o ícone é recriado a cada troca e a animação de entrada roda de novo.
export function ThemeToggle({ className = '' }: { className?: string }) {
  const theme = useTheme()
  const next = nextTheme(theme)
  const Icon = ICONS[theme]
  const label = `Tema ${THEME_LABELS[theme].toLowerCase()}. Mudar para ${THEME_LABELS[next].toLowerCase()}`
  return (
    <button type="button" className={`icon-btn theme-toggle ${className}`.trim()}
            onClick={() => setTheme(next)} aria-label={label} title={label}>
      <span key={theme} className="theme-icon"><Icon /></span>
    </button>
  )
}
