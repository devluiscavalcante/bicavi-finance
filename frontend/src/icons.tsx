// Ícones em SVG inline (traço no estilo "outline"), sem biblioteca externa.
// stroke="currentColor": o ícone herda a cor do texto do elemento pai.

import type { ReactNode } from 'react'

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  )
}

export const ChevronLeft = () => <Icon><path d="m15 18-6-6 6-6" /></Icon>

export const ChevronRight = () => <Icon><path d="m9 18 6-6-6-6" /></Icon>

export const LogOut = () => (
  <Icon>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="m16 17 5-5-5-5" />
    <path d="M21 12H9" />
  </Icon>
)

export const ArrowDown = () => <Icon><path d="M12 5v14" /><path d="m19 12-7 7-7-7" /></Icon>

export const ArrowUp = () => <Icon><path d="M12 19V5" /><path d="m5 12 7-7 7 7" /></Icon>

export const Wallet = () => (
  <Icon>
    <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
    <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
  </Icon>
)
