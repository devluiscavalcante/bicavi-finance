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

export const Plus = () => <Icon><path d="M12 5v14" /><path d="M5 12h14" /></Icon>

export const Close = () => <Icon><path d="M18 6 6 18" /><path d="m6 6 12 12" /></Icon>

export const Tag = () => (
  <Icon>
    <path d="M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.7 8.7a2.4 2.4 0 0 0 3.4 0l6.6-6.6a2.4 2.4 0 0 0 0-3.4z" />
    <circle cx="7.5" cy="7.5" r="1" fill="currentColor" />
  </Icon>
)

export const Pencil = () => (
  <Icon>
    <path d="M21.2 6.8a2.8 2.8 0 0 0-4-4L3.8 16.2a2 2 0 0 0-.5.8l-1.3 4.4a.5.5 0 0 0 .6.6l4.4-1.3a2 2 0 0 0 .8-.5z" />
    <path d="m15 5 4 4" />
  </Icon>
)

export const Trash = () => (
  <Icon>
    <path d="M3 6h18" />
    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
  </Icon>
)

export const Check = () => <Icon><path d="M20 6 9 17l-5-5" /></Icon>

export const CreditCard = () => (
  <Icon>
    <rect x="2" y="5" width="20" height="14" rx="2" />
    <path d="M2 10h20" />
  </Icon>
)
