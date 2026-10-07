import type { ReactNode } from 'react'
import { Outlet } from 'react-router'
import { ParticleField } from './ParticleField'

// Rota-pai de /login e /cadastro: o fundo de partículas fica montado enquanto o
// usuário alterna entre as duas telas (não reinicia a animação). <Outlet /> é
// onde o react-router desenha a tela filha.
export function AuthBackground() {
  return (
    <>
      <ParticleField />
      <Outlet />
    </>
  )
}

// Moldura comum das telas de login e cadastro: marca + cartão + rodapé.
export function AuthLayout({ title, subtitle, footer, children }: {
  title: string
  subtitle: string
  footer: ReactNode
  children: ReactNode
}) {
  return (
    <main className="auth">
      <div className="brand reveal">
        <span className="brand-mark">B</span>
        Bicavi
      </div>
      <section className="auth-card reveal" style={{ animationDelay: '80ms' }}>
        <div>
          <h1>{title}</h1>
          <p className="subtitle">{subtitle}</p>
        </div>
        {children}
      </section>
      <p className="auth-footer reveal" style={{ animationDelay: '160ms' }}>{footer}</p>
    </main>
  )
}
