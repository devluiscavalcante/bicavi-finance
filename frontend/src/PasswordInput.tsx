import { useState, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from './icons'

// Campo de senha com o "olho" para mostrar/ocultar o que foi digitado.
// Recebe as mesmas props de um <input> (menos o type, que é controlado aqui).
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [visible, setVisible] = useState(false)

  return (
    <span className="password-field">
      <input {...props} type={visible ? 'text' : 'password'}
             // Mostrando a senha, o teclado não deve sugerir nem corrigir palavras.
             autoCapitalize="none" autoCorrect="off" spellCheck={false} />
      <button type="button" className="password-toggle"
              aria-label={visible ? 'Ocultar senha' : 'Mostrar senha'} aria-pressed={visible}
              // Sem isto o toque no botão tira o foco do campo e o teclado do
              // celular fecha e abre de novo.
              onPointerDown={e => e.preventDefault()}
              onClick={() => setVisible(v => !v)}>
        {visible ? <EyeOff /> : <Eye />}
      </button>
    </span>
  )
}
